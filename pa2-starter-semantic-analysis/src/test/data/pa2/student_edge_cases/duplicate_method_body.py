# The body of a duplicate method is not analyzed (as in the reference).
class A(object):
    def m(self:"A"):
        pass
    def m(self:"A") -> int:
        y:int = 1
        y:int = 1
        def h() -> int:
            pass
        pass
