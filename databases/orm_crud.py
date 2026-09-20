"""SQLAlchemy ORM CRUD operations with One-to-Many relationship and cascade deletion."""

import sys
from typing import Any
from pprint import pprint

from sqlalchemy import create_engine, select
from sqlalchemy.orm import Session, sessionmaker, selectinload

from models import User, Post

# Ensure UTF-8 output encoding in Windows terminal environments
if sys.stdout.encoding.lower() != "utf-8":
    sys.stdout.reconfigure(encoding="utf-8")
if sys.stderr.encoding.lower() != "utf-8":
    sys.stderr.reconfigure(encoding="utf-8")

DATABASE_URL = "postgresql+psycopg://postgres:postgres@localhost:5432/study_db"
engine = create_engine(DATABASE_URL, echo=False)
SessionFactory = sessionmaker(bind=engine, expire_on_commit=False)


# ==========================================
# 1. CREATE (Вставка через ORM)
# ==========================================
def create_user(
    session: Session,
    username: str,
    email: str,
    bio: str | None = None,
    is_active: bool = True,
    posts_data: list[dict[str, str]] | None = None,
) -> User:
    """Создает пользователя и (опционально) связанные посты через ORM-связь."""
    user = User(username=username, email=email, bio=bio, is_active=is_active)
    if posts_data:
        for p in posts_data:
            post = Post(title=p["title"], content=p["content"])
            user.posts.append(post)  # Добавление через ORM OneToMany связь

    session.add(user)
    session.commit()
    print(f"[+] Создан пользователь '{user.username}' (id={user.id}) с {len(user.posts)} постами.")
    return user


def create_post(session: Session, user_id: int, title: str, content: str) -> Post | None:
    """Создает отдельный пост для указанного пользователя."""
    user = session.get(User, user_id)
    if not user:
        print(f"[-] Пользователь id={user_id} не найден.")
        return None

    post = Post(title=title, content=content, author=user)
    session.add(post)
    session.commit()
    print(f"[+] Для пользователя id={user_id} добавлен пост '{post.title}' (id={post.id}).")
    return post


# ==========================================
# 2. READ (Выборка через ORM)
# ==========================================
def get_user_by_id(session: Session, user_id: int) -> User | None:
    """Получает пользователя по ID вместе с его постами (жадная загрузка selectinload)."""
    stmt = (
        select(User)
        .options(selectinload(User.posts))
        .where(User.id == user_id)
    )
    return session.execute(stmt).scalar_one_or_none()


def get_all_users(session: Session) -> list[User]:
    """Возвращает всех пользователей с предзагруженными постами."""
    stmt = select(User).options(selectinload(User.posts)).order_by(User.id)
    return list(session.execute(stmt).scalars().all())


def get_all_posts(session: Session) -> list[Post]:
    """Возвращает все посты с их автором."""
    stmt = select(Post).options(selectinload(Post.author)).order_by(Post.id)
    return list(session.execute(stmt).scalars().all())


# ==========================================
# 3. UPDATE (Обновление через ORM)
# ==========================================
def update_user(session: Session, user_id: int, **kwargs: Any) -> User | None:
    """Обновляет атрибуты пользователя по ID."""
    user = session.get(User, user_id)
    if not user:
        print(f"[-] Пользователь id={user_id} не найден для обновления.")
        return None

    for key, value in kwargs.items():
        if hasattr(user, key):
            setattr(user, key, value)

    session.commit()
    print(f"[+] Обновлены данные пользователя id={user.id}: {kwargs}")
    return user


def update_post(session: Session, post_id: int, **kwargs: Any) -> Post | None:
    """Обновляет атрибуты поста по ID."""
    post = session.get(Post, post_id)
    if not post:
        print(f"[-] Пост id={post_id} не найден для обновления.")
        return None

    for key, value in kwargs.items():
        if hasattr(post, key):
            setattr(post, key, value)

    session.commit()
    print(f"[+] Обновлен пост id={post.id}: {kwargs}")
    return post


# ==========================================
# 4. DELETE (Каскадное удаление через ORM)
# ==========================================
def delete_user(session: Session, user_id: int) -> bool:
    """Удаляет пользователя по ID.

    Благодаря cascade='all, delete-orphan' в relationship и
    ondelete='CASCADE' в ForeignKey, все связанные посты удаляются каскадно.
    """
    user = session.get(User, user_id)
    if not user:
        print(f"[-] Пользователь id={user_id} не найден для удаления.")
        return False

    posts_count = len(user.posts)
    session.delete(user)
    session.commit()
    print(f"[+] Пользователь id={user_id} и все его посты ({posts_count} шт.) удалены каскадно.")
    return True


def delete_post(session: Session, post_id: int) -> bool:
    """Удаляет отдельный пост по ID без удаления автора."""
    post = session.get(Post, post_id)
    if not post:
        print(f"[-] Пост id={post_id} не найден для удаления.")
        return False

    session.delete(post)
    session.commit()
    print(f"[+] Пост id={post_id} успешно удален.")
    return True


def run_demonstration() -> None:
    """Демонстрация CRUD-операций и каскадного удаления."""
    print("=" * 70)
    print("ДЕМОНСТРАЦИЯ: SQLALCHEMY ORM CRUD С КАСКАДНЫМ УДАЛЕНИЕМ")
    print("=" * 70)

    with SessionFactory() as session:
        # Очистим таблицы перед тестом
        all_users = session.execute(select(User)).scalars().all()
        for u in all_users:
            session.delete(u)
        session.commit()
        print("[*] База данных очищена для повторного воспроизведения.\n")

        # 1. ВСТАВКА (INSERT / CREATE)
        print("--- 1. ВСТАВКА (CREATE) ---")
        user1 = create_user(
            session=session,
            username="alex_dev",
            email="alex@example.com",
            bio="Python & Database Developer",
            posts_data=[
                {"title": "Введение в SQLAlchemy 2.0", "content": "Обзор DeclarativeBase и Mapped."},
                {"title": "Миграции с Alembic", "content": "Автогенерация ревизий и применение миграций."},
            ],
        )

        user2 = create_user(
            session=session,
            username="maria_qa",
            email="maria@example.com",
            bio="QA Engineer",
            posts_data=[
                {"title": "Тестирование реляционных БД", "content": "Стратегии тестирования целостности данных."},
            ],
        )

        # Добавим еще один пост пользователю 1
        create_post(
            session=session,
            user_id=user1.id,
            title="PostgreSQL 18: Новые возможности",
            content="Подробный разбор производительности и фич.",
        )
        print()

        # 2. ВЫБОРКА (SELECT / READ)
        print("--- 2. ВЫБОРКА (READ) ---")
        fetched_user1 = get_user_by_id(session, user1.id)
        if fetched_user1:
            print(f"Пользователь: {fetched_user1.username} (email: {fetched_user1.email}, bio: '{fetched_user1.bio}')")
            print(f"Посты пользователя ({len(fetched_user1.posts)} шт.):")
            for p in fetched_user1.posts:
                print(f"  - [{p.id}] {p.title} (просмотры: {p.views_count})")
        print()

        print("Все посты в системе:")
        for post in get_all_posts(session):
            print(f"  - Пост #{post.id} '{post.title}', автор: {post.author.username}")
        print()

        # 3. ОБНОВЛЕНИЕ (UPDATE)
        print("--- 3. ОБНОВЛЕНИЕ (UPDATE) ---")
        update_user(session, user1.id, bio="Senior Backend Engineer & Tech Lead", is_active=True)
        first_post_id = fetched_user1.posts[0].id if fetched_user1 and fetched_user1.posts else 1
        update_post(session, first_post_id, views_count=150, title="Введение в SQLAlchemy 2.0 (Обновлено)")

        updated_user = get_user_by_id(session, user1.id)
        if updated_user:
            print(f"После обновления: bio='{updated_user.bio}', первый пост='{updated_user.posts[0].title}' (views={updated_user.posts[0].views_count})")
        print()

        # 4. КАСКАДНОЕ УДАЛЕНИЕ (DELETE С CASCADE)
        print("--- 4. КАСКАДНОЕ УДАЛЕНИЕ (DELETE) ---")
        total_posts_before = len(get_all_posts(session))
        user1_posts_count = len(fetched_user1.posts) if fetched_user1 else 0
        print(f"Всего постов до удаления: {total_posts_before}")
        print(f"Постов у пользователя '{user1.username}' (id={user1.id}): {user1_posts_count}")

        print(f"\nУдаляем пользователя '{user1.username}' (id={user1.id})...")
        delete_user(session, user1.id)

        # Проверяем каскадное удаление
        user1_check = get_user_by_id(session, user1.id)
        remaining_posts = get_all_posts(session)

        print(f"\nПроверка после удаления пользователя id={user1.id}:")
        print(f"  - Пользователь найден в БД? -> {user1_check is not None}")
        print(f"  - Всего постов осталось в БД: {len(remaining_posts)}")
        print("  - Оставшиеся посты в БД:")
        for p in remaining_posts:
            print(f"      * [{p.id}] '{p.title}' (автор: {p.author.username})")

        print("\n" + "=" * 70)


if __name__ == "__main__":
    run_demonstration()
