# 🧪 Laboratory

> *"I have not failed. I've just found 10,000 ways that won't work." — Thomas Edison*

## 📖 Introduction

Welcome to my code laboratory. This repository serves as a **monorepo sandbox** for my continuous learning journey. 

Unlike production repositories, the code here prioritizes **exploration** and **verification** over long-term maintainability. It contains:

- **Language Features**: Drills to master syntax and APIs (Java, Python, Go, etc.).
- **Proof of Concepts (POCs)**: Quick implementations to test architectural ideas.
- **Algorithm Drills**: LeetCode solutions and implementation of classic data structures.
- **Framework Experiments**: Minimum viable configurations to test libraries (Spring, Netty, etc.).

## 📂 Directory Structure

The structure is organized by **Domain** rather than specific technology to keep it scalable:

```text
laboratory/
├── 01-language-drills/      # Syntax, Concurrency, Stream APIs, Memory models
│   ├── java-concurrency/
│   ├── python-scripts/
│   └── ...
├── 02-algorithms/           # Data Structures & Algorithms
│   ├── leetcode-solutions/
│   └── core-impl/           # Implementation of trees, graphs, etc.
├── 03-framework-labs/       # Spring Boot, Netty, Kafka demos
│   ├── spring-tx-demo/      # Transaction propagation tests
│   └── ...
├── 04-middleware-poc/       # Redis, Docker, K8s experiments
└── 99-scratchpad/           # Temporary scripts, will be cleaned periodically