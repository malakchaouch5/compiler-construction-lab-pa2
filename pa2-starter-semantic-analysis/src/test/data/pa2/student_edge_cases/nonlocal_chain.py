# nonlocal finds the nearest declaration, two levels up.
def f() -> int:
    x:int = 1
    def g() -> int:
        def h() -> int:
            nonlocal x
            x = x + 1
            return x
        return h()
    return g()
print(f())
