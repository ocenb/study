def isLeapYear(year):
	if (year % 100 == 0) and (year % 400 == 0):
		print("Високосный год")
	elif (year % 100 != 0) and (year % 4 == 0):
		print("Високосный год")
	else:
		print("Обычный год")

isLeapYear(2024)
isLeapYear(2013)