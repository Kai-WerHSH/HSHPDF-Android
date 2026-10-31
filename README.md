# HSHPDF para Android (nativo: Kotlin + Jetpack Compose + MuPDF)

**Criador da fonte:** Henrique de Souza Henrique
**Coautor e assistente de IA:** Claude (Anthropic)

## Etapa 1 (esta versão): leitor
- Abre **PDF, EPUB, XPS, CBZ e imagens** (também ao tocar num arquivo em outros apps → "Abrir com HSHPDF").
- Rolagem contínua (páginas uma abaixo da outra), **zoom com dois dedos** e toque duplo, rolagem lateral quando ampliado.
- **Painel de miniaturas** que retrai (só números) e expande (miniatura + número); toque leva à página.
- **Buscar** texto (PDF/EPUB) com realce, anterior/próximo; **Ir para a página**; compartilhar.
- Tema claro/escuro/sistema e **cor de destaque**; lista de recentes.
- CBZ com WebP usa o decodificador do Android (não infla).

## Próximas etapas (combinadas)
2. Anotar: realçar, lápis, texto/nota, carimbos, borracha, desfazer/refazer, salvar.
3. Páginas: grade/organizar, inserir de outro arquivo, girar, excluir, em branco.
4. Converter e lote (PDF↔CBZ/EPUB/imagens), CBR (RAR), captura de tela, somente leitura, som de conclusão.

## Como gerar o APK sem instalar nada (GitHub Actions)
1. Crie uma conta e um repositório novo no GitHub (pode ser privado).
2. Envie **todo o conteúdo desta pasta** para o repositório (no site: *Add file → Upload files*, arraste os arquivos
   e a pasta `.github`; no Windows, mostre arquivos ocultos para ver `.github`).
3. Abra a aba **Actions**. O fluxo "Compilar APK" roda sozinho (ou clique em *Run workflow*). Leva uns 5–10 minutos.
4. Quando ficar verde, abra a execução e baixe o arquivo **HSHPDF-apk** (em *Artifacts*). Dentro está o `app-debug.apk`.
5. Passe o APK para o celular, toque nele e permita "instalar de fontes desconhecidas" quando pedir.

## Se a compilação falhar
Esta primeira versão foi escrita sem poder compilar no ambiente em que foi criada. É normal o primeiro build apontar
algum nome da API do MuPDF ou do Compose. Abra a execução com ❌ → clique no passo vermelho → copie as linhas que
começam com `e:` (erros) e envie para o Claude: a correção costuma ser de uma ou duas linhas.

## Observações
- MuPDF tem licença **AGPL**: sem problema para uso pessoal. Se um dia distribuir o app, a licença exige abrir o código.
- O APK gerado é "debug": assinado com chave de teste, instalável e suficiente para uso pessoal.
- A versão do MuPDF está como `1.2+` (a mais nova da série 1.2x). Para fixar uma versão, troque em `app/build.gradle.kts`.
