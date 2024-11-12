import json

with open("purchase_log.txt", "r", encoding="utf-8") as purchase_log:
    with open("visit_log.csv", "r", encoding="utf-8") as visit_log:
        with open("funnel.csv", "w", encoding="utf-8") as funnel:
            funnel.write(f"{visit_log.readline().strip()},category\n")

            purchase_log.readline()

            while True:
                purchase_line = purchase_log.readline()
                if not purchase_line:
                    break
                purchase = json.loads(purchase_line)

                for visit_line in visit_log:
                    if visit_line.startswith(purchase.get("user_id")):
                        funnel.write(
                            f"{visit_line.strip()},{purchase.get("category")}\n"
                        )
                        break
