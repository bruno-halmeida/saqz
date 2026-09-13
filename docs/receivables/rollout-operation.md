# Operação dos controles de recebimentos

A seção **Recebimentos** do adm controla a liberação técnica. Ela não habilita o BaaS
no Asaas, não publica tarifas e não cria contas financeiras automaticamente.

## Piloto por usuário

1. Em **Recebimentos → Liberação por sistema**, escolher **Usuários selecionados** para Backend e Mobile.
2. Informar motivo e salvar. A configuração só está confirmada após sucesso da API.
3. Abrir **Usuários**, localizar a pessoa e abrir **Controles de recebimentos** no detalhe.
4. Escolher **Permitir** para os sistemas desejados, informar motivo e salvar.
5. Após existir conta financeira local, a opção **Operações da conta** permite liberar/restringir
   sua operação. Aprovação cadastral, elegibilidade comercial e ativação de grupo ainda se aplicam.

A subconta criada diretamente no sandbox anteriormente não cria o vínculo financeiro local.
Os fluxos de cadastro, cobrança e carteira no aplicativo continuam sendo entregas posteriores.

## Desligamento e exceções

- **Desligado** bloqueia o sistema para todos, inclusive usuários marcados como Permitir.
- **Usuários selecionados** permite somente exceções Permitir.
- **Todos os usuários** permite todos, exceto exceções Negar.
- **Herdar regra geral** remove a exceção individual.
- Backend desligado torna Mobile indisponível para novas jornadas mesmo que Mobile esteja ligado.
- Desligar Mobile não desliga o backend. Não use visibilidade mobile como mecanismo de autorização.
- As regras técnicas não bloqueiam manutenção nem pagamento de ordens já emitidas. As permissões
  financeiras de cada ação continuam obrigatórias. As respectivas operações de pagamento/carteira
  ainda precisam ser implementadas.

## Concorrência, reenvio e histórico

Se outra edição tiver ocorrido, o painel mostra conflito e exige recarregar/revisar. Se a resposta
for incerta, use **Reenviar mesma tentativa**: o corpo e requestId originais são preservados. O painel
não permite editar essa tentativa enquanto seu resultado estiver incerto.

O histórico registra motivo, autor, alvo e antes/depois. Um reenvio confirmado retorna a resposta
original; ele não reaplica uma alteração antiga sobre a configuração atual.

## Implantação

V53 é aditiva e inicia Backend/Mobile em OFF. Não há habilitação automática em migração ou bootstrap.
Aplicar o backend antes de utilizar a seção do adm. Sem API de disponibilidade ou com erro de rede,
o mobile mantém novas jornadas indisponíveis; não guarda uma liberação antiga entre sessões.
