# nonlocal of a global, of a "global" declaration and of a local.
x:int = 0
def f():
    y:int = 0
    global x
    def g():
        def h():
            nonlocal y
            nonlocal x
            y = 1
        pass
    pass
def k():
    a:int = 1
    nonlocal a
    pass
