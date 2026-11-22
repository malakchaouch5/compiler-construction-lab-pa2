"""Random ChocoPy program generator for differential testing of PA2
(see DiffHarness.java).

Generates syntactically valid programs.  Declarations are mostly valid (so
that the type checker runs), expressions are type-directed with a
configurable error rate, so both well-typed programs (full typed-AST
comparison) and ill-typed ones (error comparison) are produced.

usage: python3 generate_programs.py OUTDIR COUNT SEED [ERROR_RATE]
"""
import os
import random
import sys

SPECIAL = ["int", "bool", "str"]


class Gen:
    def __init__(self, rnd, err):
        self.r = rnd
        self.err = err              # probability of a deliberate mistake
        self.classes = {}           # name -> dict(sup, attrs{}, methods{})
        self.order = []             # class names in order
        self.lines = []

    # ---------------------------------------------------------------- utils
    def chance(self, p):
        return self.r.random() < p

    def oops(self):
        return self.chance(self.err)

    def emit(self, indent, text):
        self.lines.append("    " * indent + text)

    def all_classes(self):
        return ["object"] + SPECIAL + self.order

    def is_sub(self, c, d):
        while c is not None:
            if c == d:
                return True
            c = self.classes[c]["sup"] if c in self.classes else (
                "object" if c in SPECIAL else None)
        return False

    def assignable(self, target, src):
        if target == src or target == "object":
            return True
        if src == "<None>":
            return target not in SPECIAL
        if src == "<Empty>":
            return target.startswith("[")
        if src.startswith("[") or target.startswith("["):
            return src == "[<None>]" and target.startswith("[") and \
                self.assignable(target[1:-1], "<None>")
        return self.is_sub(src, target)

    def rand_type(self, depth=0):
        if depth < 2 and self.chance(0.2):
            return "[" + self.rand_type(depth + 1) + "]"
        if self.oops() and self.chance(0.2):
            return self.r.choice(["Q", "Undefined"])
        return self.r.choice(self.all_classes() + SPECIAL)

    def annot(self, t):
        if t.startswith("["):
            return "[" + self.annot(t[1:-1]) + "]"
        if t in self.order and self.chance(0.6):
            return '"%s"' % t
        return t

    def literal_for(self, t):
        if self.oops():
            return self.r.choice(["1", "True", '"s"', "None"])
        if t == "int":
            return str(self.r.choice([0, 1, 42, 2147483647]))
        if t == "bool":
            return self.r.choice(["True", "False"])
        if t == "str":
            return self.r.choice(['""', '"abc"', '"x"'])
        return "None"

    # ---------------------------------------------------------- expressions
    def gen_expr(self, env, want, depth=0):
        """An expression whose type should be assignable to WANT (None = any).
        ENV maps variable names to types; it also holds 'funcs'."""
        r = self.r
        if self.oops() or want is None:
            want = self.rand_type()
        if depth > 3:
            return self.leaf(env, want)
        options = []
        vars_ok = [v for v, t in env["vars"].items()
                   if self.assignable(want, t)]
        if want == "int":
            options += ["lit", "arith", "neg", "len", "index"]
        if want == "bool":
            options += ["lit", "cmp", "logic", "not", "is", "eq"]
        if want == "str":
            options += ["lit", "concat", "strindex", "input"]
        if want.startswith("["):
            options += ["list", "listcat", "empty"]
        if want == "object":
            options += ["lit", "list", "arith", "cmp", "concat"]
        if want in self.classes or want == "object":
            options += ["new"]
        if want not in SPECIAL:
            options += ["none"]
        options += ["ifexpr", "call", "attr", "method"]
        if vars_ok:
            options += ["var"] * 4
        kind = r.choice(options)
        d = depth + 1
        g = lambda w: self.gen_expr(env, w, d)
        if kind == "var":
            return r.choice(vars_ok)
        if kind == "lit":
            return self.literal_for(want if want in SPECIAL else
                                    r.choice(SPECIAL))
        if kind == "none":
            return "None"
        if kind == "arith":
            return "(%s %s %s)" % (g("int"), r.choice("+-*%") if
                                   r.random() < .8 else "//", g("int"))
        if kind == "neg":
            return "-" + g("int")
        if kind == "len":
            return "len(%s)" % g(r.choice(["str", "[int]", "object"]))
        if kind == "index":
            return "%s[%s]" % (self.gen_value(env, "[int]", d), g("int"))
        if kind == "strindex":
            return "%s[%s]" % (g("str"), g("int"))
        if kind == "cmp":
            return "(%s %s %s)" % (g("int"), r.choice(["<", "<=", ">", ">="]),
                                   g("int"))
        if kind == "eq":
            t = r.choice(SPECIAL)
            return "(%s %s %s)" % (g(t), r.choice(["==", "!="]), g(t))
        if kind == "logic":
            return "(%s %s %s)" % (g("bool"), r.choice(["and", "or"]),
                                   g("bool"))
        if kind == "not":
            return "not " + g("bool")
        if kind == "is":
            t = r.choice(["object"] + self.order + ["[int]"])
            return "(%s is %s)" % (g(t), g(t))
        if kind == "concat":
            return "(%s + %s)" % (g("str"), g("str"))
        if kind == "listcat":
            e = want[1:-1]
            return "(%s + %s)" % (self.gen_value(env, "[%s]" % e, d),
                                  self.gen_value(env, "[%s]" % e, d))
        if kind == "empty":
            return "[]"
        if kind == "list":
            e = want[1:-1] if want.startswith("[") else self.rand_type()
            n = r.randint(1, 3)
            return "[" + ", ".join(g(e) for _ in range(n)) + "]"
        if kind == "input":
            return "input()"
        if kind == "ifexpr":
            return "(%s if %s else %s)" % (g(want), g("bool"), g(want))
        if kind == "new":
            c = want if want in self.classes else r.choice(
                self.all_classes())
            if c in SPECIAL or c == "object":
                return c + "()"
            init = self.find_method(c, "__init__")
            args = [g(t) for t in init[0][1:]] if init else []
            return "%s(%s)" % (c, ", ".join(args))
        if kind == "call":
            funcs = [(n, s) for n, s in env["funcs"].items()
                     if self.assignable(want, s[1]) and
                     (s[1] != "<None>" or want == "<None>")]
            if self.oops():
                funcs = list(env["funcs"].items())
            if not funcs:
                return self.leaf(env, want)
            name, (params, ret) = r.choice(funcs)
            nargs = len(params) + (r.choice([-1, 1]) if self.oops() else 0)
            args = [g(params[i] if i < len(params) else None)
                    for i in range(max(nargs, 0))]
            return "%s(%s)" % (name, ", ".join(args))
        if kind in ("attr", "method"):
            ok = lambda t: self.oops() or (self.assignable(want, t) and
                                           (t != "<None>" or want == "<None>"))
            cands = []
            for c in self.order:
                members = self.all_attrs(c) if kind == "attr" else {
                    m: sig[1] for m, sig in self.all_methods(c).items()}
                cands += [(c, m) for m, t in members.items() if ok(t)]
            if not cands:
                return self.leaf(env, want)
            c, m = r.choice(cands)
            obj = self.gen_value(env, c, d)
            if kind == "attr":
                return "%s.%s" % (obj, m)
            params = self.all_methods(c)[m][0][1:]
            return "%s.%s(%s)" % (obj, m, ", ".join(g(t) for t in params))
        return self.leaf(env, want)

    def gen_value(self, env, want, depth=0):
        """Like gen_expr, but never None/[] when no mistake is intended
        (used for receivers, iterables and list operands)."""
        for _ in range(8):
            e = self.gen_expr(env, want, depth)
            if self.oops() or (e not in ("None", "[]") and
                               not e.startswith("(None if") and
                               not e.endswith("else None)") and
                               "else [])" not in e and not e.startswith("([] if")):
                if want.startswith("[") and not self.oops():
                    t = self.infer_simple(env, e)
                    if t in ("<None>", "<Empty>"):
                        continue
                return e
        if want.startswith("["):
            return "[" + self.leaf(env, want[1:-1]) + "]"
        if want in self.classes:
            return self.construct(env, want, depth)
        return self.leaf(env, want)

    def infer_simple(self, env, e):
        if e in env["vars"]:
            return env["vars"][e]
        return None

    def construct(self, env, c, depth):
        init = self.find_method(c, "__init__")
        args = [self.gen_expr(env, t, depth + 1) for t in init[0][1:]] if init else []
        return "%s(%s)" % (c, ", ".join(args))

    def leaf(self, env, want):
        vars_ok = [v for v, t in env["vars"].items()
                   if self.assignable(want, t)]
        if vars_ok and not self.oops():
            return self.r.choice(vars_ok)
        if want in SPECIAL:
            return self.literal_for(want)
        if want.startswith("["):
            return "[]"
        return "None"

    def find_method(self, c, m):
        return self.all_methods(c).get(m)

    def all_attrs(self, c):
        chain = []
        while c in self.classes:
            chain.append(c)
            c = self.classes[c]["sup"]
        out = {}
        for k in reversed(chain):
            out.update(self.classes[k]["attrs"])
        return out

    def all_methods(self, c):
        chain = []
        while c in self.classes:
            chain.append(c)
            c = self.classes[c]["sup"]
        out = {"__init__": (["object"], "<None>")}
        for k in reversed(chain):
            out.update(self.classes[k]["methods"])
        return out

    # ----------------------------------------------------------- statements
    def gen_block(self, env, indent, ret, n=None, in_loop=False):
        n = n or self.r.randint(1, 3)
        for _ in range(n):
            self.gen_stmt(env, indent, ret)

    def assign_targets(self, env):
        own = env["own"]
        names = [v for v in env["vars"] if v in own]
        if self.oops():
            names = list(env["vars"]) + ["zz"]
        return names

    def gen_stmt(self, env, indent, ret, depth=0):
        r = self.r
        kinds = ["assign", "expr", "if", "while", "for", "pass", "multi",
                 "attrassign", "listassign"]
        if ret is not None:
            kinds += ["return"] * 2
        elif self.chance(self.err * 0.2):
            kinds += ["return"]
        if depth > 2:
            kinds = ["assign", "expr", "pass"]
        k = r.choice(kinds)
        g = lambda w: self.gen_expr(env, w)
        if k == "assign" or k == "multi":
            names = self.assign_targets(env)
            if not names:
                self.emit(indent, "pass")
                return
            ts = [r.choice(names) for _ in range(1 if k == "assign" else
                                                  r.randint(2, 3))]
            want = env["vars"].get(ts[0], "object")
            self.emit(indent, "%s = %s" % (" = ".join(ts), g(want)))
        elif k == "attrassign":
            holders = [c for c in self.order if self.all_attrs(c)]
            if not holders:
                self.emit(indent, "pass")
                return
            c = r.choice(holders)
            a, t = r.choice(list(self.all_attrs(c).items()))
            self.emit(indent, "%s.%s = %s" % (self.gen_value(env, c), a, g(t)))
        elif k == "listassign":
            e = self.rand_type()
            self.emit(indent, "%s[%s] = %s" % (self.gen_value(env, "[%s]" % e),
                                               g("int"), g(e)))
        elif k == "expr":
            self.emit(indent, g(None))
        elif k == "pass":
            self.emit(indent, "pass")
        elif k == "return":
            if ret is None or (ret == "<None>" and self.chance(.5)) or \
                    self.oops() and self.chance(.3):
                self.emit(indent, "return")
            else:
                self.emit(indent, "return " + g(ret))
        elif k == "if":
            self.emit(indent, "if %s:" % g("bool"))
            self.gen_block_d(env, indent + 1, ret, depth)
            while self.chance(0.3):
                self.emit(indent, "elif %s:" % g("bool"))
                self.gen_block_d(env, indent + 1, ret, depth)
            if self.chance(0.5):
                self.emit(indent, "else:")
                self.gen_block_d(env, indent + 1, ret, depth)
        elif k == "while":
            self.emit(indent, "while %s:" % g("bool"))
            self.gen_block_d(env, indent + 1, ret, depth)
        elif k == "for":
            names = self.assign_targets(env)
            if not names:
                self.emit(indent, "pass")
                return
            v = r.choice(names)
            t = env["vars"].get(v, "object")
            it = (self.gen_value(env, "[%s]" % t)
                  if self.chance(.7) or t != "str" else g("str"))
            self.emit(indent, "for %s in %s:" % (v, it))
            self.gen_block_d(env, indent + 1, ret, depth)

    def gen_block_d(self, env, indent, ret, depth):
        for _ in range(self.r.randint(1, 2)):
            self.gen_stmt(env, indent, ret, depth + 1)

    # --------------------------------------------------------- declarations
    def gen_vardef(self, env, indent, names, own):
        fresh = [n for n in names if n not in own and n not in self.order]
        if not fresh and not self.oops():
            return None
        name = self.r.choice(fresh or names)
        if self.oops() and self.chance(0.3):
            name = self.r.choice(self.all_classes())
        t = self.rand_type()
        self.emit(indent, "%s:%s = %s" % (name, self.annot(t),
                                          self.literal_for(t)))
        env["vars"][name] = t
        own.add(name)
        return name, t

    def gen_params(self, self_class=None):
        params = []
        if self_class:
            st = self_class if not self.oops() else self.r.choice(
                self.all_classes())
            params.append(("self", st))
        for i in range(self.r.randint(0, 2)):
            params.append((self.r.choice(["p", "q", "n"]) + str(i + 1)
                           if not self.oops() else self.r.choice(["p0", "int", "self"]),
                           self.rand_type()))
        return params

    def gen_func(self, env, indent, name, self_class=None, depth=0):
        params = self.gen_params(self_class)
        ret = self.rand_type() if self.chance(0.7) else "<None>"
        sig = (self.oops() and self.chance(.5))
        head = "def %s(%s)" % (name, ", ".join(
            "%s:%s" % (p, self.annot(t)) for p, t in params))
        if ret != "<None>":
            head += " -> " + self.annot(ret)
        self.emit(indent, head + ":")
        local = {"vars": dict(env["vars"]), "funcs": dict(env["funcs"]),
                 "own": set()}
        for p, t in params:
            local["vars"][p] = t
            local["own"].add(p)
        # declarations
        for _ in range(self.r.randint(0, 3)):
            k = self.r.choice(["var", "var", "global", "nonlocal", "func"])
            if k == "var":
                self.gen_vardef(local, indent + 1,
                                ["a", "b", "c", "x", "y"], local["own"])
            elif k == "global":
                cands = [v for v in self.globals_vars if v not in local["own"]]
                if not cands and not self.oops():
                    continue
                v = self.r.choice(cands) if cands and not self.oops() else "nope"
                self.emit(indent + 1, "global " + v)
                if v in self.globals_vars:
                    local["vars"][v] = self.globals_vars[v]
                    local["own"].add(v)
                    local.setdefault("globals", set()).add(v)
            elif k == "nonlocal":
                cands = [v for v in env.get("fn_vars", {}) if v not in local["own"]]
                if not cands and not self.oops():
                    continue
                if cands and not self.oops():
                    v = self.r.choice(cands)
                    local["vars"][v] = env["fn_vars"][v]
                    local["own"].add(v)
                else:
                    v = self.r.choice(["x", "a", "nope"])
                self.emit(indent + 1, "nonlocal " + v)
            elif k == "func" and depth < 2:
                inner = dict(local)
                inner["fn_vars"] = {v: local["vars"][v] for v in local["own"]
                                    if v in local["vars"] and v not in local.get("globals", set())}
                fname = self.r.choice(["inner", "helper", "k"])
                if fname in local["own"] and not self.oops():
                    continue
                local["own"].add(fname)
                sig = self.gen_func(inner, indent + 1, fname, None, depth + 1)
                local["funcs"][fname] = sig
        # statements
        n = self.r.randint(1, 3)
        for _ in range(n):
            self.gen_stmt(local, indent + 1, ret)
        if ret in SPECIAL and not self.oops():
            self.emit(indent + 1, "return " + self.gen_expr(local, ret))
        return ([t for _, t in params], ret)

    def gen_class(self, name):
        r = self.r
        sups = ["object"] + self.order
        sup = r.choice(sups)
        if self.oops():
            sup = r.choice(["int", "Later", "x", "object"] + self.order)
        self.emit(0, "class %s(%s):" % (name, sup))
        info = {"sup": sup if sup in self.order else ("object" if sup == "object" else None),
                "attrs": {}, "methods": {}}
        if info["sup"] is None:
            info["sup"] = "object"
        self.classes[name] = info
        self.order.append(name)
        n = r.randint(0, 3)
        if n == 0:
            self.emit(1, "pass")
        inherited_attrs = self.all_attrs(info["sup"]) if info["sup"] in self.classes else {}
        inherited_methods = self.all_methods(info["sup"])
        for _ in range(n):
            if r.random() < 0.5:
                aname = r.choice(["x", "y", "z", "count", "name"])
                if not self.oops() and (aname in inherited_attrs or aname in info["attrs"]
                                        or aname in info["methods"] or aname in inherited_methods):
                    aname += name.lower() + str(len(info["attrs"]))
                t = self.rand_type()
                self.emit(1, "%s:%s = %s" % (aname, self.annot(t),
                                             self.literal_for(t)))
                info["attrs"][aname] = t
            else:
                mname = r.choice(["get", "set", "run", "__init__", "m"])
                env = {"vars": dict(self.globals_vars),
                       "funcs": dict(self.funcs), "own": set()}
                if (mname in info["methods"] or mname in info["attrs"]
                        or mname in inherited_attrs) and not self.oops():
                    mname += name.lower() + str(len(info["methods"]))
                if mname in inherited_methods and not self.oops():
                    # override with the same signature
                    params, ret = inherited_methods[mname]
                    head = "def %s(self:%s%s)" % (mname, self.annot(name), "".join(
                        ", p%d:%s" % (i, self.annot(t)) for i, t in enumerate(params[1:])))
                    if ret != "<None>":
                        head += " -> " + self.annot(ret)
                    self.emit(1, head + ":")
                    local = {"vars": dict(env["vars"]), "funcs": env["funcs"],
                             "own": {"self"}}
                    local["vars"]["self"] = name
                    for i, t in enumerate(params[1:]):
                        local["vars"]["p%d" % i] = t
                        local["own"].add("p%d" % i)
                    self.gen_stmt(local, 2, ret)
                    if ret in SPECIAL:
                        self.emit(2, "return " + self.gen_expr(local, ret))
                    info["methods"][mname] = ([name] + params[1:], ret)
                else:
                    if mname == "__init__" and not self.oops():
                        mname = "act"
                    sig = self.gen_func(env, 1, mname, name)
                    info["methods"][mname] = sig

    def program(self):
        r = self.r
        self.globals_vars = {}
        self.funcs = {"print": (["object"], "<None>"),
                      "len": (["object"], "int"), "input": ([], "str")}
        env = {"vars": self.globals_vars, "funcs": self.funcs, "own": set()}
        for i in range(r.randint(0, 3)):
            self.gen_class("ABCD"[i])
        for _ in range(r.randint(1, 4)):
            self.gen_vardef(env, 0, ["x", "y", "z", "s", "l", "o"],
                            env["own"])
        for _ in range(r.randint(0, 3)):
            fname = r.choice(["f", "g", "h"])
            if fname in self.funcs and not self.oops():
                continue
            sig = self.gen_func({"vars": dict(self.globals_vars),
                                 "funcs": dict(self.funcs), "own": set()},
                                0, fname)
            self.funcs[fname] = sig
        env["own"] = set(self.globals_vars)
        for _ in range(r.randint(1, 5)):
            self.gen_stmt(env, 0, None)
        return "\n".join(self.lines) + "\n"


def main():
    out, count, seed = sys.argv[1], int(sys.argv[2]), int(sys.argv[3])
    err = float(sys.argv[4]) if len(sys.argv) > 4 else 0.05
    os.makedirs(out, exist_ok=True)
    for i in range(count):
        rnd = random.Random(seed * 100003 + i)
        try:
            text = Gen(rnd, err).program()
        except RecursionError:
            continue
        with open(os.path.join(out, "g%05d.py" % i), "w") as f:
            f.write(text)


if __name__ == "__main__":
    main()
