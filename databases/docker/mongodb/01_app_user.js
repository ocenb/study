// Для нового volume выполняется entrypoint MongoDB, для существующего — администратором.
const shop = db.getSiblingDB("shop_db");
const account = {
    user: "study_app",
    pwd: process.env.MONGO_APP_PASSWORD,
    roles: [{ role: "readWrite", db: "shop_db" }],
    mechanisms: ["SCRAM-SHA-256"],
};
if (!account.pwd) {
    throw new Error("MONGO_APP_PASSWORD is required");
}
if (shop.getUser(account.user)) {
    shop.updateUser(account.user, { pwd: account.pwd, roles: account.roles, mechanisms: account.mechanisms });
} else {
    shop.createUser(account);
}
