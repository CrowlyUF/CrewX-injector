# RECrew — Java 2.3 injetável

Base: CrewX-2.3.zip. ORIGINAL-JAVA-SHA256.txt registra os hashes da base. A integração injetável adapta hooks, mapeamentos e referências opcionais dos clients. As correções de configs, desenho e Spotify descritas abaixo também estão nesta source. O Scaffold mantém o cálculo e as opções da versão Java 2.3; as adaptações do CheatBreaker ficam nos hooks e mapeamentos.

O módulo Spotify separado foi restaurado a partir da integração antiga do FDP/Vape, incluindo a tela HTML original dentro do Minecraft. Na primeira abertura, informe um Client ID do Spotify na tela de configuração e configure `http://127.0.0.1:43791/spotify-oauth-callback` como Redirect URI no aplicativo. O login abre o navegador do Windows e, após a autorização, a tela muda automaticamente para o navegador embutido. Nas aberturas seguintes, a interface HTML roda no navegador MCEF embutido; os binários nativos do MCEF são baixados via HTTPS na primeira utilização. A API só é consultada enquanto a tela está aberta, em threads separadas. Ao fechar, o browser e o servidor local são encerrados; não há desenho nem consulta Spotify por quadro durante a partida. O Chromium nativo permanece carregado até fechar o jogo, portanto o impacto real de memória e FPS requer teste no client. O MediaPlayer existente continua independente. As credenciais locais ficam em `.minecraft/CrewX/spotify.json`.

A integração é feita em crewx/inject, nas ferramentas de empacotamento e na DLL nativa. O fluxo de instalação usa JVMTI ClassFileLoadHook + RetransformClasses com transformação ASM dentro do callback, seguindo o método usado pelo InjectMyau. Os mapeamentos named, SRG e Notch são fornecidos para os diferentes clients. A aparência do loader anterior foi mantida.

O MCEF legado tentava alterar `ClassLoader.usr_paths`, campo ausente no Java 17 usado pelo Lunar atual. No empacotamento do injetável, esse trecho é retirado de `ClientProxy.onInit`; o carregamento seguinte das DLLs continua por caminho absoluto. A verificação do build confirma a remoção nos três payloads. A inicialização nativa ainda precisa ser validada no client.

## CheatBreaker 1.8.9

O loader reconhece o CheatBreaker e usa um payload com mapeamentos específicos. O build confere o SHA-256 do `1.8.9.patch` instalado antes de gerar esse payload e valida suas referências de campos e métodos. Foram corrigidos os formatos de vértice usados nas cores do HUD e nos desenhos do mundo; a correção visual foi confirmada no client.

No Scaffold, a chamada herdada de `Entity.moveFlying` aparece no bytecode de `EntityLivingBase` com este último como dono. O mapeamento inclui esse dono para que o hook de `StrafeEvent` seja instalado e aplique a correção de movimento da source Java 2.3. Esta alteração não acrescenta uma passagem de renderização. A colocação de blocos no CheatBreaker ainda requer teste em uma sessão nova do jogo.

O menu inicial do CheatBreaker é uma classe própria. Quando ela aparece sem mundo carregado, o hook abre `CrewXMainMenu` no tick seguinte. No CheatBreaker, a ClickGUI usa apenas o desfoque dos próprios painéis; o shader de tela inteira foi desativado porque continuava sobre o mundo após fechar a tela. O renderizador do MCEF (`CefRenderer`) agora é remapeado junto com a biblioteca, evitando a busca pela classe vanilla `GlStateManager` ausente no CheatBreaker. A tela Spotify registra falhas de criação, atualização ou desenho do navegador em `%TEMP%\CrewX\crewx-inject.log`; erros de atualização têm pausa de um segundo para evitar exceções em cada quadro. A renderização do Spotify ainda exige validação no client.

O Speed no modo `Legit` da source Java 2.3 força o pulo ao andar; o modo `Normal` aplica multiplicador de velocidade. No CheatBreaker, o evento `LivingUpdate` agora é aplicado logo após a leitura do movimento do jogador, antes da lógica de pulo, porque a chamada tardia anterior não o acionava corretamente. O teste de movimento do Speed também aceita o estado físico das teclas quando o `KeyBinding` do client não o reflete. O primeiro teste com a tecla de avanço gera um registro para confirmar se o evento chega e se as condições do Speed são satisfeitas. O filtro de teclas soltas do injetável agora se aplica mesmo quando InvWalk, TargetStrafe, Speed, AntiFireball e outros módulos que não geram movimento horizontal por si só estão ligados. Isso evita movimento preso sem mudar o ataque do KillAura ou a lógica desses módulos.

O botão `Destroy` voltou ao cabeçalho da ClickGUI injetada com a confirmação de cinco segundos da versão CrewX Injector 1.2. O clique confirmado chama a rotina nativa de unload existente.

Ao fechar a tela do Spotify pela bind, o evento é marcado como consumido até a soltura da tecla ou botão. Isso impede que o polling de binds reabra a tela com o mesmo pressionamento. O tratamento só roda nesse evento de entrada; não acrescenta trabalho por quadro ao Spotify.

No Mush, NameTags lê a vida do objetivo de placar exibido abaixo do nome quando há uma pontuação para o jogador; os modos HP e Hearts e a barra usam esse valor. Sem pontuação, usa a vida da entidade. O AntiBot usa a mesma leitura para a verificação de vida zero e ignora o limite de vida da entidade quando o servidor fornece uma escala própria pelo placar. Sinais impossíveis de perfil ou rotação confirmam o bot até a remoção da entidade ou troca de mundo, evitando oscilações sem atrasar a exibição de jogadores novos. A leitura do placar é local e não adiciona consultas de rede ou outra passagem de renderização.

Os eventos de movimento/input/salto, SafeWalk e aplicação de rotação reproduzem os pontos dos Mixins da versão Java 2.3. A renderização usa WorldRenderPass da própria 2.3 para projetar uma vez por quadro. FrameOverlay desenha NameTags, ESP 2D/Outline e TargetHUD Follow uma única vez ao concluir o quadro; os handlers normais desses elementos ficam suspensos nessa fase, evitando duplicação ou cache do HUD. O Outline reaproveita as matrizes já capturadas para a máscara. Logs de cadência de renderização só são ativados com -Dcrewx.audit=true.

No Owl, o método de atualização do jogador não contém a chamada `isRiding()` usada como ponto de aplicação da rotação nos outros clients. A integração aplica a rotação imediatamente antes de `onUpdateWalkingPlayer()` quando esse ponto está ausente. O cálculo e as opções do Scaffold continuam na source Java 2.3 original; o log registra quantos pontos normais e de fallback foram instalados.

## Build

Windows x64, JDK 17, JDK 8, Visual Studio C++ e CMake. Compile uma cópia deste projeto em uma pasta temporária para manter a source de entrega limpa:

```powershell
.\Build-Injector.ps1 -Jdk17 'C:\Program Files\Java\jdk-17' -Jdk8 'C:\Program Files\BellSoft\LibericaJDK-8-Full'
```

Saída: dist/CrewXInjector.exe. Os diretórios build, native-build, build-tools e dist são artefatos de compilação e não precisam acompanhar a source em ZIP.

## Verificação

A suíte inclui os testes da source original, testes executáveis dos hooks e uma verificação de callbacks contra as classes reais do Minecraft. native/tests contém o teste do callback JVMTI numa JVM Java 8, incluindo aplicação, falhas, manutenção e restauração.

Para usar uma build nova, feche o jogo, abra novamente e injete. Compilação e testes não confirmam compatibilidade visual, FPS nem aceitação de blocos pelo servidor; esses pontos precisam ser testados no client.

## Compatibilidade de configs e Forge

Nomes como kaizen (1) são aceitos pelo importador, pelo carregamento e pelo marcador da config ativa. Os arquivos de config não precisam ser renomeados.

As chamadas opcionais de registro da conta, ataque e pipeline de luz do Forge são encaminhadas durante o empacotamento para ForgeCompat. No Forge, são delegadas ao Forge; sem ele, usam os comportamentos vanilla correspondentes. O build valida essa adaptação nos três payloads antes de gerar o EXE. Isso evita que AccountManager interrompa a inicialização do Crew no Lunar por ausência de MinecraftForge.

## Desenho que acompanha os jogadores

FrameOverlay separa o desenho dos elementos que seguem jogadores da frequência do HUD do launcher. A projeção de posições continua na passagem 3D existente; a máscara Outline usa a câmera capturada, sem recalculá-la. Não existe passagem extra do mundo nem nova leitura GL para essa mudança. Modos de ESP desenhados em 3D permanecem na passagem 3D original. Os testes verificam uma única execução por quadro, ausência de dados de quadros antigos e o caminho normal em menus/unload. FPS e resultado visual precisam de confirmação no client.

## Saúde no Mush

NameTags lê o objetivo de saúde do placar quando disponível. A lógica do AntiBot segue a source Java 2.3 original.

## Movimento lateral sem entrada física

KillAura, WTap, Velocity, Backtrack e MoveUtil são idênticos aos arquivos da source Java 2.3. No hook de entrada do injetável, quando nenhuma tecla física WASD está pressionada e nenhum módulo de movimento automático está ativo, uma entrada de movimento que restou pressionada é descartada. A primeira ocorrência de cada episódio registra os valores antes e depois dos módulos e o estado das teclas em `%TEMP%\CrewX\crewx-inject.log`. A verificação roda no tick de movimento, sem trabalho extra por frame de renderização. Confirme no client se o desvio cessou; o log distingue entrada presa de impulso ou correção do servidor.

## Spotify: reprodução de busca e Curtidas

Músicas de playlist continuam iniciando pelo contexto da playlist. Músicas de busca e Curtidas usam o contexto do álbum e o URI da faixa para escolher a música. Quando o Spotify recusa uma solicitação, a tela do navegador mostra a mensagem retornada pelo servidor local. A consulta do álbum usa dados já presentes na resposta da busca ou biblioteca, sem uma requisição adicional por música e sem trabalho durante o render do jogo.
