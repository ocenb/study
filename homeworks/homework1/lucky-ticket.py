def luckyTicket(ticket):
	ticketStr = str(ticket)
	if (int(ticketStr[0]) + int(ticketStr[1]) + int(ticketStr[2])) == (int(ticketStr[3]) + int(ticketStr[4]) + int(ticketStr[5])):
		print("Счастливый билет")
	else:
		print("Несчастливый билет")

luckyTicket(123456)
luckyTicket(123321)