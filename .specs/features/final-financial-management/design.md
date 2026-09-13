# Gestão financeira final — design

## Arquitetura escolhida

Um agregado novo `FinancialManagement` compõe o repositório de contas/delegações existente, um store
JDBC próprio de correções e um provider Asaas próprio. A correção é read-merge-write: GET comercial,
sobrescrever somente allowlist e POSTar documento completo, preservando identidade legal.

Backend: controller HTTP → autorização/application → operação JDBC cifrada → adapter Asaas. Mobile:
domain gateway → Ktor data → ViewModel MVI com generation/session → Compose compartilhado Android/iOS.
A configuração nova `ReceivablesManagementConfiguration` monta apenas esta vertical; wiring central
de rotas/Koin será entregue como diff textual ao coordenador.

## Riscos e mitigação

- POST substitutivo pode apagar campos: GET + merge completo obrigatório e teste do payload exato.
- Delegado removido durante IO: autorização imediatamente antes do IO; resultado remoto não devolve
  acesso e recuperação volta a autorizar. A janela após o último check é limite do provider sem token
  transacional; documentada no relatório.
- PII em recovery: V62 cifra payload com AAD conta/requestId; nenhum draft mobile contém valores.
- Mudança de município afeta fiscal/NFS-e: confirmação textual explícita no mobile.
- Wiring central é ownership do coordenador: classes ficam compiláveis por construtor e diff é enviado.
