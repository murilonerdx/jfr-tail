# 🦅 JFR-Tail
> **"Tail -f" para seus eventos do JVM Flight Recorder.**

🇺🇸 [Read in English](README.md)

![Build Status](https://img.shields.io/badge/build-passing-brightgreen) ![License](https://img.shields.io/badge/license-MIT-blue) ![Version](https://img.shields.io/badge/version-4.0-purple)

**JFR-Tail** traz visibilidade para sua JVM em tempo real sem o peso de APMs completos. Ele se conecta ao seu processo Java em execução, transmite eventos JFR (GC, Locks, Exceptions) e os apresenta em uma bela Interface de Terminal (TUI).

---

## 🚀 Principais Recursos

*   **TUI em Tempo Real**: Veja Garbage Collections, Thread Locks e Exceptions conforme acontecem.
*   **Segurança V4 🔒**: Autenticação Zero-Dependency usando HMAC-SHA256 JWTs.
*   **Integração Spring Boot V3 🌱**: Correlacione eventos da JVM com Actuator Health & Metrics.
*   **Pacotes de Incidente 📦**: Pressione 'B' para tirar um instantâneo imediato do estado do sistema para depuração.
*   **Leve**: Mínimo overhead (< 1% CPU), zero dependências externas para o Agente.

---

## 📦 Instalação

1.  **Compile o projeto**:
    ```bash
    ./gradlew assemble
    ```
    *Saída:* `cli/build/libs/cli-1.0-SNAPSHOT.jar` e `agent/build/libs/agent-1.0-SNAPSHOT.jar`.

---

## 🛠 Uso

### 1. Anexar a um Processo em Execução (Mais Simples)
```bash
# Encontre seu PID (ex: usando jps)
jps -l

# Anexe e monitore
java -jar cli.jar attach -p <PID> -a agent.jar
```

### 2. Conexão Segura (Recomendado)
**Lado do Servidor (App):**
```bash
java -Djfrtail.secret="minha-senha-segura" -javaagent:agent.jar -jar my-app.jar
```

**Lado do Cliente (Você):**
```bash
java -jar cli.jar connect --secret "minha-senha-segura"
```

### 3. Concedendo Acesso Temporário
Não compartilhe sua senha mestra! Gere um token temporário:
```bash
# Gere um token válido por 1 hora
java -jar cli.jar token --secret "minha-senha-segura" --ttl 3600
```
Entregue a string gerada ao seu desenvolvedor. Ele conecta usando:
```bash
java -jar cli.jar connect --token "eyJhbGciOiJIUzI1Ni..."
```

---

## 🌱 Integração Spring Boot
Inicie a CLI com detalhes do Actuator para habilitar o **Painel Spring**:

```bash
java -jar cli.jar connect \
  --secret "minha-senha-segura" \
  --actuator-url "http://localhost:8080/actuator"
```
**Dentro da TUI:**
*   Pressione **`S`** para alternar a Visão Spring (Saúde + Top Requests).
*   Pressione **`B`** para exportar um Pacote de Incidente (Incident Bundle).

---

## 📚 Documentação
Para comandos detalhados, detalhes de segurança e opções de configuração, veja o [Manual do Usuário](docs/USAGE_PT.md).

---

## 📄 Licença
Licença MIT.
