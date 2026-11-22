# Assignments to variables of outer scopes (rule 8).
x:int = 1
def f() -> int:
    w = 3
    x = 2
    for x in [1]:
        pass
    return 1
def g():
    f = 1
print = 1
f = 2
