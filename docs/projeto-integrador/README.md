# Projeto Integrador — Demo como material didático aberto

> Documento inicial do Projeto Integrador (Fase 3 — Criando Projetos e Enviando Candidaturas),
> disciplina *Projeto Integrador: Profissional de TI – Empregabilidade e Portfólio*,
> Bacharelado em Sistemas de Informação, Gran Centro Universitário.
> Autor: Wilbert de Almeida Joosen — Brasília/DF, 2026.

## 1. O problema social

O Brasil forma por ano cerca de **53 mil** pessoas em cursos de perfil tecnológico, mas a demanda
média anual do mercado é de **159 mil** profissionais. O resultado é um déficit estimado de
**106 mil talentos por ano** (Brasscom, estudo *Demanda de Talentos em TIC e Estratégia ΣTCEM*, 2021).

O déficit não é só de quantidade. As vagas mais disputadas pedem experiência com sistemas
distribuídos, mensageria, contêineres, entrega contínua e monitoramento em produção. Projetos
acadêmicos pequenos quase nunca exercitam essas competências. O material gratuito costuma mostrar
cada tecnologia isolada e raramente explica **por que** cada decisão foi tomada. Quem não tem acesso
a cursos pagos ou a uma primeira experiência numa empresa grande fica sem um caminho prático para
aprender.

## 2. A proposta

Este repositório é um **sistema real, aberto e comentado**: um comércio eletrônico com 18 serviços
Spring Boot e uma interface em Vue 3. Ele pode ser clonado, executado na própria máquina e estudado
parte por parte. O [README principal](../../README.md) registra, para cada tecnologia, o problema que
ela resolve, por que foi escolhida e em qual arquivo pode ser estudada. Também traz um glossário de
termos técnicos.

### Objetivo geral

Disponibilizar uma arquitetura de referência em microsserviços como material didático prático para
a formação de profissionais de TI.

### Objetivos específicos

- Reunir em um único sistema funcional as práticas de engenharia exigidas pelo mercado.
- Aplicar e documentar padrões de projeto e de sistemas distribuídos, indicando onde estudá-los.
- Demonstrar uma esteira completa de CI/CD com ambientes de teste e de produção via GitOps.
- Demonstrar observabilidade de ponta a ponta: métricas, logs, rastros e perfilamento.

### Quem se beneficia

- Estudantes de cursos de tecnologia, especialmente de cursos a distância.
- Profissionais de outras áreas em transição de carreira para TI.
- Desenvolvedores juniores e plenos que querem se preparar para vagas de back-end e arquitetura.
- Professores e instrutores que precisam de um sistema completo para usar como exemplo em aula.

## 3. Tecnologias

| Área | Tecnologias |
|---|---|
| Back-end | Java, Spring Boot, Spring Cloud Gateway, Eureka, Config Server, Spring Data, Spring Kafka / Kafka Streams, Spring Security, Resilience4j |
| Front-end | Vue 3, TypeScript, Element Plus, Pinia, Vue Router, vue-i18n, ECharts, Tailwind CSS, Vite, lint |
| Dados e mensageria | MongoDB (cluster com *sharding* e *replica sets*), MySQL, Redis, Elasticsearch, Apache Kafka |
| Identidade | Keycloak (OAuth2 / OpenID Connect), HashiCorp Vault |
| Entrega | Docker, Kubernetes (k3d), Kustomize, Argo CD, GitHub Actions, GHCR |
| Observabilidade | Prometheus, Grafana, Loki, Tempo, Pyroscope, Kibana, k6 |

## 4. Roteiro de estudo pelo código

Siga esta ordem, do mais simples ao mais avançado:

1. **Executar o sistema** — comece pela opção B (Docker Compose) do [Quick start](../../README.md#quick-start).
2. **Um serviço por dentro** — `product-service/`: controlador → serviço (interface) → repositório (SOLID, Repository).
3. **Saga e eventos** — os pacotes `saga/` de `order-service`, `payment-service`, `shipping-service` e `delivery-service`.
4. **CQRS** — `order-service`: escrita em MySQL, leitura em projeção no MongoDB.
5. **Circuit Breaker** — `order-service/.../config/Resilience4jConfig.java`.
6. **Ports & Adapters e Strategy** — `product-media-service` (`StoragePort`) e `inventory-service` (`ReorderPolicy`).
7. **Segurança** — `common-security/` e a configuração do Keycloak em `docker/keycloak/`.
8. **Esteira e GitOps** — `.github/workflows/ci-cd.yml` e `k8s-argocd/`.
9. **Observabilidade** — `k8s/platform/monitoring/`.

## 5. Fluxo de desenvolvimento

| Ramificação | Papel |
|---|---|
| `develop` | Desenvolvimento diário: novas funcionalidades e correções |
| `testing` | Esteira executa testes, gera a imagem de cada serviço alterado e implanta o ambiente de QA |
| `main` | Promove para produção exatamente as imagens validadas em QA, sem recompilar |

## 6. Limitações e próximos passos

- [ ] Traduzir a documentação principal para o português.
- [ ] Adicionar uma licença de código aberto (por exemplo, MIT).
- [ ] Criar trilhas de estudo com exercícios por padrão e por tecnologia.
- [ ] Oferecer um perfil reduzido do sistema para computadores com menos memória.

## Referências

- BRASIL tem um déficit anual de 106 mil talentos em TI. *Convergência Digital*, 16 dez. 2021.
  Disponível em: <https://convergenciadigital.com.br/carreira/brasil-tem-um-dficit-anual-de-106-mil-talentos-em-ti>.
- NEWMAN, Sam. *Building microservices*. 2. ed. O'Reilly Media, 2021.
- RICHARDSON, Chris. *Microservices patterns*: with examples in Java. Manning, 2018.
- HUMBLE, Jez; FARLEY, David. *Entrega contínua*. Bookman, 2014.
