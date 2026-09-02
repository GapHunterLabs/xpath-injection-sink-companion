# XPath Injection Sink Companion

Flags an `XPath.evaluate(...)`/`.compile(...)` call whose argument is
built from an HTTP endpoint parameter.

## Why it exists

CWE-643 (XPath Injection), documented in the OWASP Testing Guide: an
attacker's raw input becomes XPath SOURCE TEXT, re-parsed and
evaluated. AWS CodeGuru Detector Library has a dedicated detector
(severity High) -- CI/batch, not an inline IDE inspection. No
dedicated Marketplace plugin found for this exact angle.

## Why built this way

- **A real hand-written XPath 1.0 parser** -- the THIRD full custom
  grammar in this catalog, after `redos-catastrophic-backtracking-
  companion`'s regex grammar and `spel-injection-sink-companion`'s
  SpEL grammar. Tokenizer + recursive-descent parser building a real
  AST (location paths, steps, predicates, function calls).
- **Taint alone is the vulnerability, flagged unconditionally** --
  same reasoning as this catalog's SpEL plugin: once tainted data is
  confirmed reaching the argument, the attacker controls the
  substituted text's shape, so no "AST danger shape" gate would be
  sound (unlike SpEL, XPath has no arbitrary-method-invocation
  primitive, so there's no separate "hardcoded dangerous type" finding
  here either).
- **The grammar's real job is noise reduction, not the security
  finding itself** -- it reconstructs the argument's static skeleton
  (every tainted/non-constant operand replaced by a fixed placeholder)
  and confirms the skeleton parses as well-formed XPath before
  flagging, skipping a `.evaluate(...)` call whose argument clearly
  isn't XPath source text at all.

## v0.1 scope — stated honestly, not exhaustively

- Only Java; only same-method taint (a direct reference, or a `+`
  concatenation of literals and tainted references).
- `looksLikeXPath` is a declared-type-TEXT heuristic (or a
  `XPathFactory....newXPath()` call chain), never resolved against the
  real `javax.xml.xpath` classpath.
- The grammar supports location paths, predicates, function calls,
  `and`/`or`/`div`/`mod` -- no XML namespaces (`ns:tag`) in v0.1.

## Usage

Open a Java file with a Spring MVC/JAX-RS endpoint method that builds
an XPath expression from a request parameter -- the `evaluate`/
`compile` call shows a warning.

## Enterprise / Team Licensing

Need enterprise features, custom rules, or team licensing? Contact us at
**gaphunterlabs@gmail.com**.

## Development

```
./gradlew test           # unit tests
./gradlew buildPlugin    # generates build/distributions/*.zip
./gradlew verifyPlugin   # checks compatibility against real IDEs
```

## License

Apache-2.0. See `LICENSE`.
