import multiprocessing
import time
from multiprocessing import shared_memory

def producer(shm_name, data_to_send, buffer_size):
    existing_shm = shared_memory.SharedMemory(name=shm_name)
    print(f"Производитель: Подключен к общей памяти '{shm_name}'.")

    print(f"Производитель: Попытка записи данных: '{data_to_send}'")
    encoded_data = data_to_send.encode('utf-8')

    if len(encoded_data) > buffer_size:
        print(f"Производитель: Ошибка! Данные слишком большие для буфера. Макс. размер: {buffer_size} байт.")
        return

    existing_shm.buf[:len(encoded_data)] = encoded_data
    existing_shm.buf[len(encoded_data):buffer_size] = b'\x00' * (buffer_size - len(encoded_data))

    print("Производитель: Данные записаны.")
    existing_shm.close()

def consumer(shm_name, buffer_size):
    existing_shm = shared_memory.SharedMemory(name=shm_name)
    print(f"Потребитель: Подключен к общей памяти '{shm_name}'.")

    print("Потребитель: Ожидание данных...")
    time.sleep(0.1)

    read_data_bytes = existing_shm.buf[:].tobytes()
    decoded_data = read_data_bytes.decode('utf-8').strip('\x00')

    print(f"Потребитель: Получены данные: '{decoded_data}'")
    existing_shm.close()
    return decoded_data

if __name__ == "__main__":
    buffer_size = 1024

    shm = shared_memory.SharedMemory(create=True, size=buffer_size)
    print(f"Главный процесс: Создан сегмент общей памяти с именем: '{shm.name}'")

    data = "Привет из производителя (через shared_memory)!"

    print("Запуск процессов производителя и потребителя...")
    producer_process = multiprocessing.Process(target=producer, args=(shm.name, data, buffer_size))
    consumer_process = multiprocessing.Process(target=consumer, args=(shm.name, buffer_size))

    producer_process.start()
    consumer_process.start()

    producer_process.join()
    consumer_process.join()

    print("Процессы производителя и потребителя завершены.")

    shm.close()
    shm.unlink()
    print("Общая память освобождена и удалена.")