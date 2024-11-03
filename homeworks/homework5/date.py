from datetime import datetime

def parseDate(date):
	formats = [
		('%A, %B %d, %Y', 'The Moscow Times'),
		('%A, %d.%m.%y', 'The Guardian'),
		('%A, %d %B %Y', 'Daily News')
	]

	for format, newspaper in formats:
		try:
			parsedDate = datetime.strptime(date, format)
			print(parsedDate, newspaper)
			return
		except ValueError:
			continue
	print('Формат не подходит')

while True:
	inp = input('Введите дату или "exit" для выхода: ')
	if inp.lower() == 'exit':
		break
	parseDate(inp)