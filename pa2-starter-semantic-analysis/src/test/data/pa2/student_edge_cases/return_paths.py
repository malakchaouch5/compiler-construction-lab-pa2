# Return paths: if/elif/else, loops and non-special return types.
class A(object):
    pass
def f() -> A:
    pass
def g() -> [int]:
    pass
def h() -> bool:
    if True:
        pass
    elif False:
        return True
    else:
        return False
def k() -> int:
    while True:
        return 1
