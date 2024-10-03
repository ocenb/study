import math

def middleLetter(word):
	wordLenght = len(word)
	if wordLenght % 2 == 0:
		mid = wordLenght // 2
		print(word[mid - 1] + word[mid])
	else:
		print(word[math.trunc(wordLenght / 2)])

middleLetter('test')
middleLetter('testing')