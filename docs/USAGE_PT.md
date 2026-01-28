# Manual do Usuário JFR-Tail

🇺🇸 [Read in English](USAGE.md)

## Visão Geral
**JFR-Tail** é uma ferramenta de observabilidade leve que permite que você faça "tail" (acompanhe) eventos do Java Flight Recorder (JFR) em tempo real. Ela fornece uma UI baseada em terminal (TUI) para monitorar internos da JVM, correlacionados com métricas da aplicação.

## V4 Segurança & Autenticação
A partir da Versão 4, o JFR-Tail impõe segurança estrita usando HMAC-SHA256 JWTs.

### 1. Modo Proprietário (Acesso Total)
Quando você inicia o Agente (ou anexa a uma JVM), você possui um **Segredo Compartilhado** (Shared Secret). Este segredo te dá controle administrativo.

**Encontrando o Segredo:**
- Olhe nos logs STDOUT da aplicação na inicialização:
  ```
  [SECURITY] SECRET=550e8400-e29b-41d4-a716-446655440000
  ```
- OU, defina manualmente via Propriedade de Sistema:
  ```bash
  java -Djfrtail.secret=minha-senha-segura -javaagent:jfrtail-agent.jar ...
  ```

**Conectando como Proprietário:**
A CLI irá gerar automaticamente um token de curta duração usando o segredo.
```bash
jfr-tail connect --secret "minha-senha-segura"
```

### 2. Modo Convidado (Acesso Temporário)
Se você deseja conceder acesso temporário a um desenvolvedor/SRE sem compartilhar o segredo mestre:

1.  **Gere um Token (Proprietário):**
    ```bash
    # Gere token válido por 30 minutos (1800 segundos)
    jfr-tail token --secret "minha-senha-segura" --ttl 1800
    ```
    *Saída:* `eyJhbGciOiJIUzI1NiJ9.eyJleHAiOjE3...`

2.  **Conecte (Convidado):**
    ```bash
    jfr-tail connect --token "eyJhbGciOiJIUzI1NiJ9..."
    ```
    *Nota: O convidado será desconectado automaticamente quando o token expirar.*

---

## Integração Spring Boot (V3)
O JFR-Tail pode se integrar com o Spring Boot Actuator para mostrar Status de Saúde e Métricas HTTP ao lado de eventos da JVM.

### Configuração
Garanta que seu App Spring Boot exponha o Actuator:
```properties
management.endpoints.web.exposure.include=health,metrics,threaddump,env
```

### Conectando com Spring & Segurança
```bash
jfr-tail connect \
  --secret "minha-senha-segura" \
  --actuator-url "http://localhost:8080/actuator" \
  --actuator-user "admin" \  # Opcional (se o Actuator for protegido)
  --actuator-pass "secret"
```

### Navegação TUI
- **Tecla `S`**: Alterna **Painel Spring**. Mostra Status de Saúde (UP/DOWN) e Top Endpoints.
- **Tecla `B`**: Cria **Pacote de Incidente**. Comprime stats atuais, logs e info de trace em um arquivo.
- **Tecla `C`**: Limpa tela atual.
- **Tecla `Q`**: Sair.

---

## Dashboard Web
O agente hospeda um dashboard leve em:
`http://localhost:8080/jfr/dashboard?token=<SEU_TOKEN>`

Você deve gerar um token válido (`jfr-tail token ...`) e passá-lo no parâmetro de consulta da URL.

## Solução de Problemas
**"AUTH FAILED"**:
- Verifique se seu Token expirou.
- Verifique se o Segredo corresponde ao do servidor.
- Garanta que o horário do servidor esteja sincronizado.

**"Address already in use"**:
- A porta do agente (7099) ou porta web (8080) está ocupada. Use portas diferentes via argumentos de linha de comando se possível (Requer suporte do Agente) ou mate o processo conflitante.
