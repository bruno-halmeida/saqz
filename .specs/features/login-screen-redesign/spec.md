# Especificação do Redesign da Tela de Login

**Status:** Aprovada pela conversa de 2026-07-17
**Referência:** imagem fornecida pelo usuário, adaptada ao escopo de autenticação vigente

## Problema

A tela compartilhada de login já oferece as ações corretas, mas sua composição
visual ainda parece um formulário genérico e não comunica a identidade Saqz nem
a hierarquia da referência aprovada.

## Objetivo

Entregar em Android e iOS uma tela de login visualmente próxima da referência,
com branding centralizado, campos iconográficos, ação principal destacada,
separação clara do Google e fundo decorativo Saqz, sem alterar o fluxo de
autenticação.

## Fora de Escopo

| Item | Motivo |
| --- | --- |
| Login por telefone | O fluxo vigente aceita somente email e senha. |
| Apple Sign-In | Continua fora do escopo da feature de autenticação. |
| Facebook Login | Não pertence ao contrato vigente. |
| Mudanças em Firebase, backend ou adapters nativos | O trabalho é exclusivamente de apresentação compartilhada. |

## Critérios de Aceite

1. **LOGIN-UI-01** — WHEN a tela de login for exibida THEN SHALL apresentar, nesta
   ordem visual, marca Saqz, headline `Organize seu grupo. Jogue junto.`, texto de
   apoio, campos de email e senha, recuperação de senha, ação `Entrar`, divisor
   `ou continue com`, ação `Entrar com Google` e convite `Criar conta`.
2. **LOGIN-UI-02** — WHEN as opções de autenticação forem renderizadas THEN SHALL
   existir somente email, senha e Google; telefone, Apple e Facebook SHALL não
   aparecer.
3. **LOGIN-UI-03** — WHEN o usuário editar campos ou acionar entrar, Google,
   recuperação ou cadastro THEN SHALL executar os mesmos callbacks e estados de
   loading/erro já definidos, incluindo senha mascarada e controle de visibilidade.
4. **LOGIN-UI-04** — WHEN a tela usar viewport compacto ou fonte em escala 2x THEN
   SHALL manter todas as ações alcançáveis por rolagem, alvos mínimos de 48 dp,
   nomes acessíveis e ordem de leitura equivalente à ordem visual.
5. **LOGIN-UI-05** — WHEN a tela estiver ociosa THEN SHALL mostrar fundo claro,
   motivo Saqz sutil no topo e ondas azuis no rodapé sem adicionar nós semânticos
   nem bloquear interação.
6. **LOGIN-UI-06** — WHEN o arquivo de login for aberto no tooling Compose THEN
   SHALL oferecer preview sem parâmetros no fim do próprio arquivo, com dimensões
   de telefone e conteúdo representativo.

## Verificação

- Testes Compose cobrem conteúdo, exclusões, callbacks, estados, acessibilidade e
  viewport compacto.
- Compilações Android e iOS do módulo de acesso passam.
- Inspeção visual do preview confirma hierarquia, proporções e decoração contra a
  referência aprovada.
