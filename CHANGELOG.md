# Changelog

All notable changes to the **JSONata** plugin are documented here.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

- Custom functions (JSONata prelude + JVM `JsonataFunctionProvider` classes) are implemented but
  hidden from the UI while being tested and fine-tuned (`CUSTOM_FUNCTIONS_ENABLED`).

## [0.1.0]

First public release.

### Added
- Live JSONata playground in a split editor (JSON left, expression + result right), per JSON file.
- Syntax highlighting for JSONata expressions (custom lexer + language).
- Error annotations driven by the real engine (syntax errors underlined in place).
- Autocomplete for built-in functions (with signatures) and for JSON field keys
  (path evaluated against the bound JSON, so it works through predicates and function calls).
- Parameter info for built-in functions.
- Runs on the full IntelliJ Platform (build 243+) — IntelliJ IDEA, PyCharm, WebStorm, GoLand,
  PhpStorm, RubyMine, CLion, Rider, and other JetBrains IDEs.

[Unreleased]: https://github.com/tix/intellij-jsonata/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/tix/intellij-jsonata/releases/tag/v0.1.0
