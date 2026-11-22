# Programming Assignment 2

[PA2 Specification]: https://studip.uni-passau.de/studip/dispatch.php/course/files?cid=1158ee3975665902d07585a824234cb1

[ChocoPy Language Manual and Reference]: https://studip.uni-passau.de/studip/dispatch.php/course/files?cid=1158ee3975665902d07585a824234cb1

Note: Users running Windows should replace the colon (`:`) with a semicolon (`;`)
in the classpath argument for all commands listed below.

## Getting started

Run the following command to build your semantic analysis, and then run all
the provided tests:

```bash
mvn clean package

java -cp "chocopy-ref.jar:target/assignment.jar" chocopy.ChocoPy --pass=.s --dir src/test/data/pa2/sample/ --test
```

In the starter code, only two tests should pass. Your objective is to implement
a semantic analysis that passes all the provided tests and meets the assignment
specifications.

You can also run the semantic analysis on one input file at a time. In general,
running the semantic analysis on a ChocoPy program is a two-step process. First,
run the reference parser to get an AST JSON. In the following command and the
rest of the commands below, replace placeholders in `<>` with actual file names:

```bash
java -cp "chocopy-ref.jar:target/assignment.jar" chocopy.ChocoPy --pass=r <chocopy_input_file> --out <ast_json_file>
```

Second, run the semantic analysis on the AST JSON to get a typed AST JSON:

```bash
java -cp "chocopy-ref.jar:target/assignment.jar" chocopy.ChocoPy --pass=.s  <ast_json_file> --out <typed_ast_json_file>
```

The `src/tests/data/pa2/sample` directory already contains the AST JSONs for
the test programs (with extension `.ast`); therefore, you can skip the first
step for the sample test programs.

To observe the output of the reference implementation of the semantic analysis,
replace the second step with the following command:

```bash
java -cp "chocopy-ref.jar:target/assignment.jar" chocopy.ChocoPy --pass=.r <ast_json_file> --out <typed_ast_json_file>
```

In either step, you can omit the `--out <output_file>` argument to print the
JSON to standard output instead.

You can combine AST generation by the reference parser with your semantic
analysis as well:

```bash
java -cp "chocopy-ref.jar:target/assignment.jar" chocopy.ChocoPy --pass=rs <chocopy_input_file> --out <typed_ast_json_file>
```

## Assignment specifications

See the [PA2 Specification][]
for a detailed specification of the assignment.

Refer to the [ChocoPy Language Manual and Reference][]
for the specification of the ChocoPy language.


## Submission write-up

### Team members (Group 4)

- Omar Kamel
- Malak Chaouch

### Acknowledgements

- *ChocoPy Language Manual and Reference* (Chapter 5, type rules and
  Section 2.4, type hierarchy) and the PA2 specification.
- Slides of the *Compiler Construction* lecture (C. Hammer) on semantic
  analysis, scopes and symbol tables, and type checking.
- The reference implementation `chocopy-ref.jar`, which we used as a test
  oracle (see *Testing* below).
- AI tools: we used Claude (Anthropic) as an assistant for this
  assignment: for implementing and debugging parts of the analysis, for
  writing the differential-testing tools in `tools/difftest` and for
  proofreading this write-up. We reviewed and tested all code.
- No collaboration with other groups.

### Time spent

About 35 hours in total for the whole team.

### Contributions

We worked on all parts of the assignment together, mostly in joint
sessions: the declaration analysis (`DeclarationAnalyzer`, `ClassInfo`,
`ClassHierarchy`), the `TypeAnnotationChecker`, the type checker
(`TypeChecker`) with its error recovery, the student and edge-case tests,
the differential testing and this write-up. We reviewed every part
together, so both of us can explain the whole implementation.

### Overview of the implementation

All code is in `src/main/java/chocopy/pa2`:

| File | Purpose |
| --- | --- |
| `StudentAnalysis.java` | Entry point; runs the passes. |
| `DeclarationAnalyzer.java` | Pass 1: symbol tables, class hierarchy, rules 1-7 and 9. |
| `TypeAnnotationChecker.java` | Pass 2: rule 11 (classes in type annotations). |
| `TypeChecker.java` | Pass 3: type inference, typing rules, rules 8 and 10. |
| `ClassInfo.java` | A class: super-class, attributes and methods (with the inherited ones). |
| `ClassHierarchy.java` | All classes, subtyping `<=`, assignability `<=a` and join. |
| `ReturnPaths.java` | Helper for rule 9: does a statement list return on every path? |

### Testing

- All 59 sample tests pass, as do our three student tests and the 16 tests
  in `src/test/data/pa2/student_edge_cases` (small programs for the special
  cases listed under Question 2):
  ```bash
  java -cp "chocopy-ref.jar:target/assignment.jar" chocopy.ChocoPy --pass=.s --dir src/test/data/pa2/student_edge_cases --test
  ```
- **Differential testing.** `tools/difftest/generate_programs.py` generates
  random, syntactically valid ChocoPy programs (classes with inheritance and
  overriding, nested functions with `global`/`nonlocal`, all statements and
  expressions). Declarations are mostly correct and expressions are chosen
  by type, with a configurable rate of deliberate mistakes.
  `tools/difftest/DiffHarness.java` runs the reference analysis and ours
  on every program. For a correct program the complete typed AST has to be
  equal (every inferred type and location). For an incorrect program every
  reference error (message and start location) has to be reported by us,
  and we also list errors that only we report.
- We generated 39,500 programs with error rates from 0 % to 40 % and
  added the PA1 and PA2 test programs and our hand-written probes. Of the
  36,657 programs without syntax errors, our analysis reports exactly the
  same errors as the reference for every one, without a single extra
  error. 3,410 of them are correct programs, and for all of these the
  typed ASTs are identical.

```bash
python3 tools/difftest/generate_programs.py /tmp/gen 2000 1 0.05
javac -cp "chocopy-ref.jar:target/assignment.jar" -d tools/difftest/out tools/difftest/DiffHarness.java
java -cp "chocopy-ref.jar:target/assignment.jar:tools/difftest/out" DiffHarness /tmp/gen src/test/data/pa2
```

### Question 1: How many passes does the semantic analysis make?

Three passes over the AST, started by `StudentAnalysis.process`:

1. **`DeclarationAnalyzer`** analyzes all declarations. It first enters the
   predefined classes (`object`, `int`, `bool`, `str`) and functions
   (`print`, `input`, `len`) into the global symbol table and collects the
   names of all classes. In step 1 it binds every global name in program
   order: variables to their `ValueType`, functions to a `FuncType` and
   classes to a `ClassInfo`. A `ClassInfo` holds the super-class and all
   attributes and methods, inherited ones included, and is added to the
   `ClassHierarchy`. Because this happens in program order, a super-class
   must be defined before it is used (rule 4). The members of a class are
   checked against the inherited ones (rules 5-7) and against each other
   (rule 1). In step 2 it builds one `SymbolTable` per function and method
   body, with the enclosing scope as parent: parameters, local variables,
   `global`/`nonlocal` declarations and nested functions (rules 1-3). It
   also checks that functions returning `int`, `bool` or `str` return on
   every path (rule 9, using `ReturnPaths`). Every global name is known in
   step 2, so a body can use functions and classes that are defined later.
2. **`TypeAnnotationChecker`** visits every type annotation (of variables,
   attributes, parameters and return types) and reports class names that
   do not exist (rule 11). It is a separate pass because the reference
   checks annotations everywhere, even inside declarations that pass 1
   rejects, such as the body of a duplicate method.
3. **`TypeChecker`** uses the symbol tables and the class hierarchy to
   infer the type of every expression, according to the typing rules of
   the manual, and to report type errors with the recovery of Section
   5.3.2. The subtype relation, assignability (`<=a`) and the join are
   implemented once in `ClassHierarchy` and used by all rules. Pass 3 also
   checks the two rules that need statements: assignments to variables
   that are not declared in the current scope (rule 8) and `return` at the
   top level (rule 10). Like the reference, pass 3 only runs if passes 1
   and 2 found no error, since the symbol tables of an incorrect program
   are incomplete.

### Question 2: What was the hardest component to implement?

The declaration analysis, and in particular matching the reference
exactly when one construct breaks several rules. The rules themselves are
short, but the hidden tests require that we report *every* error of the
reference with the same message at the same position. The specification
leaves the order of the checks open, and only one error is kept per AST
node, so the order of the checks decides the message. With the
differential tester we found these behaviours of the reference (each one
has a test in `student_edge_cases`):

- A class member is only added to the class if it passes all checks, and
  a later member is a *duplicate* only if an earlier member of the same
  name was added. A method is checked in the order: re-defines an attribute,
  different signature than the inherited method, wrong first parameter
  (`DeclarationAnalyzer.analyze(ClassDef)` and `isValidMethod`). The body
  of a duplicate method is not analyzed.
- `nonlocal x` takes the nearest declaration of `x`, starting with the
  current scope, and rejects it only if it is global or declared `global`.
  So `nonlocal x` two levels up is fine, while `nonlocal x` for a local `x`
  is reported as a duplicate (`analyze(NonLocalDecl)`).
- `global` and `nonlocal` names may reuse class names, but parameters,
  local variables and nested functions may not. The shadowing rule also
  covers classes that are defined later in the program.

The type checker had similar details. In `s[0] = "c"` (a `str`) the target
is first checked as an index expression, and "`str` is not a list type" is
only reported if that check found nothing. After "Cannot iterate", the
loop variable is not checked. `<None>` and `<Empty>` inherit `__init__`
from `object`. In a multiple assignment, a mismatched target comes before
the `[<None>]` rule. We found each of these by comparing outputs, writing
a minimal example, checking it with the reference, and only then
changing the code.

### Question 3: Why infer the most specific type for ill-typed expressions?

If an expression is ill-typed, we infer the type that its typing rule
would give without the failing premise. The error is reported once, and
the rest of the program is checked as if the expression had its "intended"
type. If we inferred `object` for every ill-typed expression, one mistake
would cause a cascade of errors in all enclosing expressions and
statements. Examples from `student_contributed/bad_types.py`:

- Line 42, `x = (1 + "two") * 2`: `+` on `int` and `str` is an error, but
  one operand is an `int`, so we infer `int`. Then `* 2` is fine and the
  assignment to the `int` variable `x` is fine: **one** error. With
  `object` we would also report `*` on `object` and `int`, and the
  assignment of an `object` to an `int`: three errors for one mistake.
- Line 43, `b = (x == "zero") and True`: `==` always gives a `bool`, so
  neither `and` nor the assignment to `b` reports an error.
- Lines 44, 45 and 48: `items[True]` still has the element type `int` of
  `[int]`, and `twice(b)` and `sq.area(False)` still have the declared
  return type `int`. Only the bad index and the bad arguments are reported,
  not the `+ 1`, the `-` or the `% 3` around them.
- Line 50, `x = undefined_name + 1`, shows what happens without a specific
  type: an unknown name can only get `object`, so the `+` fails as well
  (two errors). Inferring `object` everywhere would make every line look
  like this.

So inferring the most specific type gives one message for each actual
mistake, at the place where it is. Messages caused by earlier errors would
hide the real errors and confuse the programmer. It also lets the checker
keep checking the code around an error, for example the arguments of a
call whose result was used wrongly.

### Bug reports and remarks on the course material

1. **Rejected parameters disappear from method signatures.** If a parameter
   shadows a class name (or repeats a parameter name), the reference drops
   it from the method's signature, so the override check compares the
   wrong signatures. For
   ```python
   class A(object):
       def m(self:"A", int:int):
           pass
   class B(A):
       def m(self:"B", x:int):
           pass
   ```
   the reference reports `Cannot shadow class name: int` and also
   `Method overridden with different type signature: m` at line 5,
   although both methods have the type `(int) -> <None>`. In the other
   direction, with `def m(self:"A", int:int, a:bool)` in `A` and
   `def m(self:"B", a:bool)` in `B`, the different number of parameters is
   not reported. Expected: only the shadowing error in the first case, and
   both errors in the second.
2. **Duplicate methods are only partly analyzed.** In the body of a method
   that is declared twice in a class, the reference reports no errors
   (e.g. a duplicate local variable), but it still reports missing return
   statements of functions nested in that body:
   ```python
   class A(object):
       def m(self:"A"):
           pass
       def m(self:"A"):
           y:int = 1
           y:int = 1        # not reported
           def h() -> int:  # reported: All paths ... must have a return statement: h
               pass
           pass
   ```
   Duplicate functions (not methods) are fully analyzed.
3. **For loops lose errors about the loop variable.** For
   `for z in 5: pass` with an undefined `z`, the reference only reports
   `Cannot iterate over value of type int` and not `Not a variable: z`.
   For `for y in [1]: pass` it reports `Not a variable: y` at the location
   of the whole `for` statement instead of the identifier `y`, as it does
   for assignments (Section 5.3.2).
4. **Inconsistent message:** `return` without a value in a function
   returning `str` gives ``Expected type `str`; got `None` ``, while every
   other message says ``got type `<None>` ``.
5. **Specification:** Section 7.3 names the file `bad_type.py`, but the
   starter code and Section 4 use `bad_types.py`. Section 5.1.2 calls the
   kind `NonlocalDecl`, but the AST class and the JSON use `NonLocalDecl`.
   Section 5.1.3 speaks of a `locations` property, but it is called
   `location`. In the `FuncType` kind a comma is missing after
   `parameters: [ValueType]`.
6. **Starter README:** it refers to `src/tests/data/pa2/sample`; the
   directory is `src/test/data/pa2/sample`.

## Acknowledgments

This starter code is adapted from the work of the **ChocoPy team**
(see [this repository](https://github.com/cs164berkeley/pa2-chocopy-semantic-analysis)).
For more information, visit [ChocoPy.org](https://chocopy.org/).


