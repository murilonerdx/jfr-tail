# JFR-Tail (Java Flight Recorder TUI)

**JFR-Tail** é uma ferramenta de linha de comando (CLI) com interface gráfica textual (TUI) que funciona como um `tail -f` para eventos do Java Flight Recorder. Ela permite monitorar Garbage Collection, Locks, Exceptions e outros eventos em tempo real de uma JVM em execução, sem a necessidade de dumps de arquivos `.jfr`.

![Status](https://img.shields.io/badge/status-MVP-green)

## 📋 Funcionalidades

- **Monitoramento em Tempo Real**: Conecta-se a um processo Java (PID) e faz streaming de eventos.
- **TUI Interativa**: Visualização colorida e organizada no terminal.
- **Estatísticas**: Contadores em tempo real de GC, Locks e Exceções.
- **Filtros**: Capacidade de filtrar eventos por nome (simples).
- **Leve**: Usa a tecnologia padrão do JDK (JFR Streaming) e um agente Java otimizado.

---

## 🛠️ Pré-requisitos

- **JDK 17** ou superior (testado com JDK 21).
- **Gradle** (opcional, o projeto inclui wrapper, mas recomenda-se ter instalado).
- **Terminal** com suporte a cores ANSI (PowerShell, Bash, Git Bash).

---

## 🚀 Como Compilar

O projeto é um monorepo Gradle. Para gerar os executáveis (JARs), execute:

```bash
# Windows
./gradlew build

# Linux/Mac
./gradlew build
```

Isso gerará os seguintes artefatos principais:
- **CLI (Fat JAR)**: `cli/build/libs/cli-1.0-SNAPSHOT-all.jar`
- **Agent**: `agent/build/libs/agent-1.0-SNAPSHOT.jar`
- **Sample App**: `sample/build/libs/sample-1.0-SNAPSHOT.jar`

---

## 🎮 Como Usar

### 1. Teste Automatizado (Recomendado)
Para verificar se tudo está funcionando, utilize o script de automação incluído. Ele compila o projeto, inicia uma aplicação de teste e conecta o `jfr-tail` automaticamente.

```powershell
# No PowerShell
.\run_test.ps1
```

O script irá:
1. Compilar o projeto.
2. Rodar o `SampleApp` em segundo plano.
3. Abrir a TUI do `jfr-tail` conectada ao SampleApp.
4. Ao fechar a TUI (pressionando `q`), o SampleApp será encerrado.

### 2. Uso Manual (Passo a Passo)

Caso queira monitorar sua própria aplicação:

#### Passo A: Obtenha o PID da aplicação alvo
Descubra o ID do processo Java que deseja monitorar (`jps` ou Gerenciador de Tarefas).

```bash
jps
# Exemplo de saída:
# 12345 MinhaAplicacao
```

#### Passo B: Execute o comando `attach`
Use o JAR da CLI para "anexar" o agente ao processo alvo. Você precisa indicar onde está o JAR do agente.

```bash
java -jar cli/build/libs/cli-1.0-SNAPSHOT-all.jar attach \
  --pid <PID_DA_SUA_APP> \
  --agent-jar agent/build/libs/agent-1.0-SNAPSHOT.jar
```

**Exemplo real:**
```bash
java -jar cli/build/libs/cli-1.0-SNAPSHOT-all.jar attach --pid 12345 --agent-jar agent/build/libs/agent-1.0-SNAPSHOT.jar
```

---

## 🖥️ Controles da TUI

Ao iniciar a interface, você verá os eventos rolando na tela.

| Tecla | Função |
| :---: | --- |
| `q` | **Sair**: Fecha a TUI e desconecta. |
| `c` | **Limpar**: Reseta a lista de eventos e os contadores de estatísticas. |
| `Esc` | **Sair**: Alternativa para fechar. |

*(Nota: Filtros avançados por tecla `f` estão planejados para a v2)*

---

## 🧩 Arquitetura

O projeto é dividido em 3 módulos:

1.  **`agent`**: Um Java Agent que usa `jdk.jfr.consumer.RecordingStream`. Ele entra no processo alvo, assina eventos (GC, MonitorEnter, Throwables) e os envia como **JSON Lines** via Socket TCP (porta padrão 7099).
2.  **`cli`**: A aplicação cliente. Usa a **Attach API** para injetar o agente dinamicamente e a biblioteca **Lanterna** para desenhar a interface no terminal. Lê o stream JSON do socket e renderiza.
3.  **`common`**: Classes de modelo (`JfrEvent`) compartilhadas entre agente e cliente.

---

## ⚠️ Solução de Problemas

**Erro: "AttachNotSupportedException: no providers installed"**
- Certifique-se de estar usando o mesmo JDK (ou versão compatível) para rodar o `jfr-tail` que está rodando a aplicação alvo.

**Erro de conexão recusada**
- O agente pode não ter subido corretamente. Verifique se a aplicação alvo tem permissão para abrir portas locais ou se a porta 7099 já está em uso.

**Caracteres estranhos no terminal**
- Verifique se seu terminal suporta codificação UTF-8 e cores ANSI (use Windows Terminal ou Git Bash no Windows).
