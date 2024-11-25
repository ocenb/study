import threading
import time


def formula1(start, end):
    for x in range(start, end):
        _ = x**2 - x**2 + x**4 - x**5 + x + x


def formula2(start, end):
    for x in range(start, end):
        _ = x + x


def formula3(iterations):
    for i in range(iterations):
        _ = i + i


def parallel_execution(iterations):
    thread1 = threading.Thread(target=formula1, args=(1, iterations + 1))
    thread2 = threading.Thread(target=formula2, args=(1, iterations + 1))

    start_time = time.time()
    thread1.start()
    thread2.start()

    thread1.join()
    time_formula1 = time.time() - start_time

    thread2.join()
    time_formula2 = time.time() - start_time - time_formula1

    start_time_formula3 = time.time()
    formula3(iterations)
    time_formula3 = time.time() - start_time_formula3

    return time_formula1, time_formula2, time_formula3


def main():
    for iterations in [10000, 100000]:
        print(f"Количество итераций: {iterations}")
        time_formula1, time_formula2, time_formula3 = parallel_execution(iterations)
        print(f"Формула 1: {time_formula1:.6f} сек")
        print(f"Формула 2: {time_formula2:.6f} сек")
        print(f"Формула 3: {time_formula3:.6f} сек")
        print("\n")


if __name__ == "__main__":
    main()
