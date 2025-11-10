# Contributing to TitaniumBOE-Sim

Thank you for your interest in contributing! 🎉

## 🚀 Quick Start

1. **Fork** the repository
2. **Clone** your fork
3. **Create** a feature branch
4. **Make** your changes
5. **Test** thoroughly
6. **Submit** a pull request

---

## 🏗️ Development Setup

```bash
# Clone your fork
git clone https://github.com/YOUR_USERNAME/TitaniumBOE-Sim.git
cd TitaniumBOE-Sim

# Build and test
mvn clean install

# Run tests
mvn test

# Run with coverage
mvn test jacoco:report
```

---

## 📝 Code Style

- **Java 21** features preferred
- **Clear** variable names
- **Minimal** comments (code should be self-documenting)
- **JavaDoc** for public APIs
- **Follow** existing patterns

### Example:
```java
// ❌ Bad
public void doStuff(int x) { ... }

// ✅ Good
public void processOrder(int orderId) { ... }
```

---

## ✅ Testing Requirements

All contributions must include:
- **Unit tests** for new functionality
- **Integration tests** for API changes
- **Coverage** maintained above 70%
- **All tests** passing

```bash
# Run tests
mvn test

# Check coverage
mvn jacoco:report
open target/site/jacoco/index.html
```

---

## 🔄 Pull Request Process

1. **Update** documentation if needed
2. **Add** tests for new features
3. **Ensure** CI passes
4. **Request** review
5. **Address** feedback

### PR Title Format:
```
feat: Add new order type support
fix: Correct price calculation in matching engine
docs: Update API documentation
refactor: Simplify authentication logic
test: Add integration tests for WebSocket
```

---

## 🐛 Bug Reports

Include:
1. **Description** of the bug
2. **Steps** to reproduce
3. **Expected** behavior
4. **Actual** behavior
5. **Environment** (OS, Java version)
6. **Logs** if available

---

## 💡 Feature Requests

Include:
1. **Use case** description
2. **Proposed** solution
3. **Alternatives** considered
4. **Additional** context

---

## 🎯 Areas for Contribution

### Good First Issues:
- Documentation improvements
- Test coverage increase
- Code comments/JavaDoc
- Example scripts

### Advanced:
- New order types
- Performance optimizations
- Additional trading bots
- Protocol extensions

---

## 📊 Quality Gates

Your PR must pass:
- ✅ Build successful
- ✅ All tests passing
- ✅ Coverage ≥ 70%
- ✅ No security vulnerabilities
- ✅ Code review approved

---

## 🤝 Code of Conduct

- Be respectful and inclusive
- Provide constructive feedback
- Focus on the code, not the person
- Help others learn and grow

---

## 📞 Questions?

- **Issues**: For bugs and features
- **Discussions**: For questions and ideas
- **Email**: For sensitive matters

---

Thank you for contributing! 🙏
