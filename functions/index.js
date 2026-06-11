/**
 * Gising! Cloud Functions
 * ───────────────────────────────────────────────────────────────────────────
 * Two server-side guarantees that can't be done safely from the client:
 *
 *  1. onUserDeleted   — when a Firebase Auth account is deleted, erase every trace
 *                       of that user from Firestore. The Android app also cascades
 *                       client-side, but if it's killed mid-delete this is the
 *                       backstop that satisfies "right to erasure" (Play policy).
 *
 *  2. onMemberJoined  — when someone joins a circle, push "X joined your circle" to
 *                       the other members. The Android app only RECEIVES pushes
 *                       (GisingMessagingService); sending must happen here with a
 *                       trusted credential.
 *
 * Uses the 1st-gen API namespace (firebase-functions/v1) because Auth `onDelete`
 * background triggers are only available there.
 *
 * Deploy:  firebase deploy --only functions
 */
const functions = require("firebase-functions/v1");
const admin = require("firebase-admin");

admin.initializeApp();
const db = admin.firestore();

// ── 1. Guaranteed data deletion ─────────────────────────────────────────────
exports.onUserDeleted = functions.auth.user().onDelete(async (user) => {
  const uid = user.uid;

  const circles = await db
    .collection("circles")
    .where("memberUids", "array-contains", uid)
    .get();

  for (const circleDoc of circles.docs) {
    const isOwner = circleDoc.get("ownerUid") === uid;
    if (isOwner) {
      // Delete the whole circle the user owned (members subcollection + the doc).
      const members = await circleDoc.ref.collection("members").get();
      const batch = db.batch();
      members.forEach((m) => batch.delete(m.ref));
      batch.delete(circleDoc.ref);
      await batch.commit();
    } else {
      // Just remove this user's membership from circles they merely belonged to.
      const batch = db.batch();
      batch.delete(circleDoc.ref.collection("members").doc(uid));
      batch.update(circleDoc.ref, {
        memberUids: admin.firestore.FieldValue.arrayRemove(uid),
      });
      await batch.commit();
    }
  }

  // Invites the user created.
  const invites = await db
    .collection("invites")
    .where("createdBy", "==", uid)
    .get();
  const inviteBatch = db.batch();
  invites.forEach((inv) => inviteBatch.delete(inv.ref));
  await inviteBatch.commit();

  // The user's private profile.
  await db.collection("users").doc(uid).delete().catch(() => {});

  functions.logger.info(`Erased Firestore data for deleted user ${uid}`);
});

// ── 2. "Someone joined your circle" push ─────────────────────────────────────
exports.onMemberJoined = functions.firestore
  .document("circles/{circleId}/members/{memberUid}")
  .onCreate(async (snap, context) => {
    const { circleId, memberUid } = context.params;
    const newMember = snap.data() || {};

    const circleSnap = await db.collection("circles").doc(circleId).get();
    const circle = circleSnap.data();
    if (!circle) return;

    // Notify everyone in the circle EXCEPT the person who just joined.
    const otherUids = (circle.memberUids || []).filter((u) => u !== memberUid);
    if (otherUids.length === 0) return; // e.g. the owner creating their own member doc

    const tokens = [];
    const tokenOwners = []; // parallel array: which uid each token belongs to
    for (const uid of otherUids) {
      const userSnap = await db.collection("users").doc(uid).get();
      const token = userSnap.get("fcmToken");
      if (token) {
        tokens.push(token);
        tokenOwners.push(uid);
      }
    }
    if (tokens.length === 0) return;

    const message = {
      notification: {
        title: circle.name || "Your circle",
        body: `${newMember.displayName || "Someone"} joined your circle.`,
      },
      data: { type: "member_joined", circleId },
      tokens,
    };

    const response = await admin.messaging().sendEachForMulticast(message);

    // Clean up tokens that are no longer valid so we don't keep pushing to them.
    const stale = [];
    response.responses.forEach((res, i) => {
      const code = res.error && res.error.code;
      if (
        code === "messaging/registration-token-not-registered" ||
        code === "messaging/invalid-registration-token"
      ) {
        stale.push(tokenOwners[i]);
      }
    });
    await Promise.all(
      stale.map((uid) =>
        db.collection("users").doc(uid).update({
          fcmToken: admin.firestore.FieldValue.delete(),
        }).catch(() => {})
      )
    );

    functions.logger.info(
      `Notified ${tokens.length} member(s) that ${newMember.displayName} joined ${circleId}`
    );
  });
