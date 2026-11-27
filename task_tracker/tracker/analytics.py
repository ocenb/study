import json
from enum import StrEnum
from io import BytesIO
from textwrap import fill
from threading import Lock

import pandas as pd
from fastapi import APIRouter, Response
from matplotlib.figure import Figure
from matplotlib.ticker import MaxNLocator

from .db import DB
from .tasks import Filters, Selection

router = APIRouter(prefix="/analytics", tags=["Аналитика"])
plot_lock = Lock()


class Group(StrEnum):
    status = "status"
    topic = "topic"
    assignee = "assignee"


def summarize(db, filters: Selection, group_by: Group):
    frame = pd.read_sql_query(
        f"SELECT status, topic, assignee FROM tasks WHERE {filters.where}",
        db,
        params=filters.values,
    )
    counts = (
        frame.groupby(group_by.value, dropna=False)
        .size()
        .sort_values(ascending=False, kind="stable")
    )
    total = len(frame)
    done = int(frame["status"].eq("done").sum())
    return {
        "group_by": group_by.value,
        "total": total,
        "done": done,
        "completion_percent": round(100 * done / total, 1) if total else 0.0,
        "groups": [
            {"value": None if pd.isna(label) else label, "count": int(count)}
            for label, count in counts.items()
        ],
    }


@router.get("")
def analytics(db: DB, filters: Filters, group_by: Group = Group.status):
    return summarize(db, filters, group_by)


def chart_label(value, group_by, statuses):
    if group_by == Group.status:
        return statuses[value]
    if value is None:
        return "Без темы" if group_by == Group.topic else "Не назначен"
    # Кавычки отделяют заданное пользователем имя от подписи пустой группы.
    return fill(json.dumps(value, ensure_ascii=False), width=28, drop_whitespace=False)


@router.get(
    "/chart.png",
    response_class=Response,
    responses={200: {"content": {"image/png": {}}}},
)
def chart(db: DB, filters: Filters, group_by: Group = Group.status):
    result = summarize(db, filters, group_by)
    statuses = dict(db.execute("SELECT code, label FROM statuses"))
    groups = result["groups"][:12]
    labels = [chart_label(g["value"], group_by, statuses) for g in groups]
    lines_per_label = max((label.count("\n") + 1 for label in labels), default=1)
    row_height = max(0.46, 0.22 * lines_per_label)
    titles = {
        Group.status: "по статусам",
        Group.topic: "по темам",
        Group.assignee: "по исполнителям",
    }
    with plot_lock:
        fig = Figure(figsize=(8, max(3, 1.6 + row_height * len(groups))), layout="constrained")
        ax = fig.subplots()
        ax.set_title(
            "Задачи "
            + titles[group_by]
            + (" · первые 12 групп" if len(result["groups"]) > 12 else "")
        )
        if groups:
            bars = ax.barh(range(len(groups)), [g["count"] for g in groups], color="#19756a")
            ax.set_yticks(range(len(groups)), labels, parse_math=False)
            ax.invert_yaxis()
            ax.bar_label(bars, padding=4)
            ax.margins(x=0.15)
            ax.xaxis.set_major_locator(MaxNLocator(integer=True))
            ax.set_xlabel("Количество задач")
            ax.spines[["right", "top"]].set_visible(False)
        else:
            ax.set_axis_off()
            ax.text(0.5, 0.5, "По выбранным фильтрам задач нет", ha="center", va="center")
        output = BytesIO()
        fig.savefig(output, format="png", dpi=120)
    return Response(output.getvalue(), media_type="image/png")
