package com.pydroidx.app

object ProjectWorkspace {
    val templates = listOf("Blank", "Hello World", "CLI App", "Calculator", "Automation", "Guessing Game", "CSV Data")

    fun templateSource(template: String): String = when (template) {
        "Blank" -> ""
        "CLI App" -> """while True:
    choice = input("1. Say hello  2. Exit: ")
    if choice == "1":
        print("Hello!")
    elif choice == "2":
        break
    else:
        print("Choose 1 or 2")
"""
        "Calculator" -> """first = float(input("First number: "))
operator = input("Operation (+, -, *, /): ")
second = float(input("Second number: "))

if operator == "+":
    print(first + second)
elif operator == "-":
    print(first - second)
elif operator == "*":
    print(first * second)
elif operator == "/" and second != 0:
    print(first / second)
else:
    print("Unknown operation or division by zero")
"""
        "Automation" -> """from pathlib import Path

for file in Path(".").iterdir():
    if file.is_file():
        print(file.name, file.stat().st_size, "bytes")
"""
        "Guessing Game" -> """import random

answer = random.randint(1, 10)
while True:
    guess = int(input("Guess 1 to 10: "))
    if guess == answer:
        print("You got it!")
        break
    print("Try again")
"""
        "CSV Data" -> """import csv

rows = [{"name": "Alex", "score": 8}, {"name": "Sam", "score": 10}]
with open("scores.csv", "w", newline="", encoding="utf-8") as file:
    writer = csv.DictWriter(file, fieldnames=["name", "score"])
    writer.writeheader()
    writer.writerows(rows)

with open("scores.csv", newline="", encoding="utf-8") as file:
    for row in csv.DictReader(file):
        print(row["name"], row["score"])
"""
        else -> "print(\"Hello world!\")\n"
    }

    fun safeName(value: String): String = value.trim()
        .replace(Regex("[^A-Za-z0-9 _-]+"), " ")
        .replace(Regex(" +"), " ")
        .trim(' ', '.', '_', '-')
        .take(40)
        .ifBlank { "Project" }

    fun nextName(existing: Collection<String>, base: String = "Project"): String {
        val cleanBase = safeName(base)
        if (cleanBase !in existing) return cleanBase
        var number = 2
        while ("$cleanBase $number" in existing) number++
        return "$cleanBase $number"
    }
}
