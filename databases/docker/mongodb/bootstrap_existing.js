// Явная настройка существующего volume без удаления документов.
// localhost exception разрешает создать первого администратора, если пользователей нет.
const admin = db.getSiblingDB("admin");
const username = process.env.MONGO_INITDB_ROOT_USERNAME;
const password = process.env.MONGO_INITDB_ROOT_PASSWORD;
let authenticated = false;
try {
    authenticated = Boolean(admin.auth(username, password).ok);
} catch (error) {
    if (error.code !== 18) throw error;
}
if (!authenticated) {
    admin.createUser({ user: username, pwd: password, roles: [{ role: "root", db: "admin" }] });
    admin.auth(username, password);
}
load("/docker-entrypoint-initdb.d/01_app_user.js");
print("MongoDB administrator and shop_db application user configured");
