ringsCount = int(input('Введите количество колец (от 1 до 8) '))

file = open("решение.txt", "w", encoding="utf-8")

def hanoi(ringsCount, a, b, c):
	if ringsCount == 1: # если диск один, то перемещаем диск с стержня A на стержень C
		print(a, '->', c)
		file.write(f'{a} -> {c}\n')
	else: # если дисков больше одного, то рекурсивно перемещаем n-1 дисков на стержень С, затем переносим n-1 дисков со стержня B на стержень A
		hanoi(ringsCount - 1, a, c, b)
		print(a, '->', c)
		file.write(f'{a} -> {c}\n')
		hanoi(ringsCount - 1, b, a, c)
	
hanoi(ringsCount, 'Стержень 1', 'Стержень 2', 'Стержень 3')

movesCount = 2 ** ringsCount - 1
print(f'Число ходов: {movesCount}')
file.write(f'Число ходов: {movesCount}')