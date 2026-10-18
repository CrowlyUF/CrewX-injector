# RECrew — Java 2.3 injetável

Base: CrewX-2.3.zip. Os módulos da source Java original foram preservados. ORIGINAL-JAVA-SHA256.txt registra os hashes da base. Config.java e ClickGui.java receberam a correção de nomes com parênteses e diagnóstico do carregamento; as adaptações de desenho por quadro estão em ESP.java, NameTags.java, TargetHUD.java e CameraMatrices.java; os demais 354 arquivos Java originais continuam idênticos. Não inclui Scaffold/Rise, suavização híbrida nem a fila de colocação das builds anteriores.

A integração é feita em crewx/inject, nas ferramentas de empacotamento e na DLL nativa. O fluxo de instalação usa JVMTI ClassFileLoadHook + RetransformClasses com transformação ASM dentro do callback, seguindo o método usado pelo InjectMyau. Os mapeamentos named, SRG e Notch são fornecidos para os diferentes clients. A aparência do loader anterior foi mantida.

Os eventos de movimento/input/salto, SafeWalk e aplicação de rotação reproduzem os pontos dos Mixins da versão Java 2.3. A renderização usa WorldRenderPass da própria 2.3 para projetar uma vez por quadro. FrameOverlay desenha NameTags, ESP 2D/Outline e TargetHUD Follow uma única vez ao concluir o quadro; os handlers normais desses elementos ficam suspensos nessa fase, evitando duplicação ou cache do HUD. O Outline reaproveita as matrizes já capturadas para a máscara. Logs de cadência de renderização só são ativados com -Dcrewx.audit=true.

No Owl, o método de atualização do jogador não contém a chamada `isRiding()` usada como ponto de aplicação da rotação nos outros clients. A integração aplica a rotação imediatamente antes de `onUpdateWalkingPlayer()` quando esse ponto está ausente. O cálculo e as opções do Scaffold continuam na source Java 2.3 original; o log registra quantos pontos normais e de fallback foram instalados.

## Build

Windows x64, JDK 17, JDK 8, Visual Studio C++ e CMake. Compile uma cópia deste projeto em uma pasta temporária para manter a source de entrega limpa:

```powershell
.\Build-Injector.ps1 -Jdk17 'C:\Program Files\Java\jdk-17' -Jdk8 'CAMINHO_DO_JDK_8'
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
