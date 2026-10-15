# Módulo Media Player (antigo "Spotify") — como funciona e como configurar

O módulo mostra um card "Tocando agora" (capa, título, artista, barra de progresso, controles) e, logo abaixo,
a letra sincronizada. Ele funciona com **qualquer mídia**: app do Spotify, SoundCloud, YouTube e qualquer site
no navegador, VLC etc.

## Para o jogador (zero configuração)

1. Nada: o módulo **Media Player** já vem ligado (fica escondido da ArrayList).
2. Toque algo (Spotify, SoundCloud, YouTube...). O card aparece sozinho **só enquanto há música tocando**; ao
   pausar ele some depois de ~4 s (com o chat aberto ele continua visível para dar play ou arrastar).

Configs antigas do módulo "Spotify" são aproveitadas automaticamente. Comandos: `.spotify`, `.sp`, `.media`
ou `.mediaplayer`; para ligar/desligar: `.t mediaplayer`.

No Windows isso é lido do "controle de mídia do sistema" (o mesmo do painel de volume). Não precisa de conta,
login nem navegador. No Linux é preciso o `playerctl`; no macOS funcionam os apps Spotify e Música.

Com o **chat aberto** (T): arraste o card em qualquer parte para mover; clique em ⏮ ⏯ ⏭ para controlar; clique
na barra para pular para um ponto da música.

### Tamanho do card

- `card-size` (slider, 50 %–200 %, padrão 100 %): aumenta/diminui o card inteiro (capa, título, artista, barra,
  controles e letra) na hora, sem reiniciar o módulo. Fica salvo na config.
- `compact-width` (180–460) é a largura do card em 100 %; `card-size` multiplica esse valor.
- Limites: o card nunca fica mais estreito que 150 px (abaixo disso título e artista se encostam) e nunca passa de
  70 % da largura da tela e sempre cabe na altura dela (com a letra incluída). Em telas/GUI scale muito pequenos o slider "trava"
  nesses limites.

### Requisitos por sistema (detecção real)

| Sistema | Como lê | O que precisa |
|---------|---------|---------------|
| Windows 10 (1809+) / 11 | `smtc.ps1` (PowerShell 5.1, já vem no Windows) lê o SMTC | Nada. Se uma política da empresa/escola bloquear o PowerShell, o card mostra o erro em `.spotify status`. Opcional: `-Dcrewx.media.powershell=<caminho do pwsh.exe>`. |
| Linux | `playerctl --all-players` (MPRIS) | Instalar `playerctl`. O player que está **tocando** tem prioridade sobre abas pausadas. |
| macOS | AppleScript (apps Spotify e Música) | Permitir "Automação" quando o macOS pedir. Navegadores (SoundCloud/YouTube) **não** são expostos a scripts no macOS. |

SoundCloud, YouTube, Deezer etc. no navegador funcionam no Windows e no Linux porque o navegador publica a sessão de
mídia do sistema (Chrome, Edge, Firefox, Opera, Brave). No Firefox para Linux, `media.hardwaremediakeys.enabled`
precisa estar ligado (padrão).

## Fontes (propriedade `source`)

| Valor    | O que usa |
|----------|-----------|
| Auto     | Mídia do sistema; se não houver nada tocando, consulta a API do Spotify (ex.: música tocando no celular). |
| System   | Só a mídia do sistema. |
| Spotify  | Só a API do Spotify (precisa do login). |

## Login do Spotify (opcional)

Só serve para ver o que toca em **outros dispositivos** (celular, caixa de som) e para trazer a capa/flag de
conteúdo explícito exatos. Se o seu build tiver um Client ID, ao ativar o módulo o navegador abre uma vez para o
jogador autorizar. Depois fica salvo. Comandos: `.spotify connect`, `.spotify logout`, `.spotify source <auto|system|spotify>`.

### Limite importante da Spotify (2026)

Um app Spotify em **Development Mode** aceita no máximo **5 contas** (todas cadastradas à mão em *User Management*
no dashboard) e o dono precisa de Premium. Qualquer outra conta recebe HTTP 403: o módulo detecta isso, avisa uma
vez no chat e continua funcionando pela mídia do sistema. O *Extended Quota Mode* (sem limite de contas) hoje
exige empresa registrada e 250 mil usuários ativos. Por isso, para um client público, a mídia do sistema é o
caminho principal e o login do Spotify é só um complemento.

### Como configurar o Client ID (você, dono do client)

1. Em https://developer.spotify.com/dashboard crie um app (API: Web API).
2. Em *Redirect URIs* cadastre **as três**:
   `http://127.0.0.1:8888/callback`, `http://127.0.0.1:8889/callback`, `http://127.0.0.1:8890/callback`
   (a Spotify não aceita `localhost`; o módulo usa a primeira porta livre).
3. Copie o **Client ID** (nunca o Client Secret) e cole em `SpotifyApiSource.BUILTIN_CLIENT_ID`
   (`src/main/java/crewx/media/SpotifyApiSource.java`). Quem quiser usar o próprio app pode preencher a
   propriedade `client-id` do módulo.
4. Adicione em *User Management* as contas que podem usar a API (máx. 5).

## Capa

- **Windows**: a capa vem da miniatura da sessão de mídia (a mesma do painel de volume). O helper grava cada
  versão num arquivo próprio (`config/CrewX/media/cover.img.<n>`) e tenta ler a miniatura a cada 0,5 s nos
  primeiros segundos da faixa (players costumam publicá-la depois do título) e depois a cada 3 s enquanto
  não houver capa. Se a leitura falhar, o motivo aparece em `.spotify status` ("cover error: ...").
- **Linux**: `mpris:artUrl` do player (Spotify, navegador). Links antigos `open.spotify.com/image/...` são
  convertidos para `i.scdn.co/image/...`.
- **macOS**: o app Spotify informa a capa; o app Música não expõe capa por AppleScript (fica sem capa).
- Quando a música troca, a capa anterior sai na hora; a nova entra quando carregar. Falhas de rede são
  tentadas de novo (até 3 vezes).

## Letras

O card mostra **só o trecho do momento atual** (uma linha, no máximo 2 linhas quebradas), sincronizado com a
música; ao avançar, o trecho anterior sobe e some e o próximo aparece. Na introdução e em pausas
instrumentais longas não aparece nada. Linhas repetidas seguidas não "piscam" (o trecho continua na tela).
Letras sem tempo (só texto) são distribuídas ao longo da música e também aparecem um trecho por vez, sem
repetições seguidas — nesse caso o tempo é estimado.


Buscadas no LRCLIB (https://lrclib.net): primeiro por correspondência exata (título, artista, álbum, duração),
depois por títulos "limpos" (sem "(feat. ...)", "[Official Video]", "- Topic" etc.), depois pela busca com
ranking por título/artista/duração. Envios "de teste" do LRCLIB (ex.: `[00:00.00]probe` para uma música de
3 min) são descartados: músicas com mais de 1 min e menos de 3 linhas não são aceitas. Guardadas em `config/CrewX/lyrics/` (não baixa de novo). Se a letra
estiver adiantada/atrasada, ajuste `lyrics-offset` (ms).

Aviso: a política de desenvolvedor do Spotify restringe sincronizar gravações com mídia visual; as letras vêm
do LRCLIB, não do Spotify, mas verifique se o uso se aplica ao seu caso.

## Estrutura do código

```
crewx/media/
  MediaManager        junta tudo: escolhe a fonte, carrega capa e letra em segundo plano
  MediaInfo           "foto" imutável do que está tocando (posição é extrapolada)
  SystemMediaSource   mídia do sistema  -> backend/WindowsMediaBackend (PowerShell + SMTC)
                                           backend/LinuxMediaBackend   (playerctl)
                                           backend/MacMediaBackend     (AppleScript)
  SpotifyApiSource    login PKCE + polling adaptativo + controles
  LyricsService       LRCLIB + cache em memória/disco   (LyricsQuery, LrcParser, Lyrics, LyricLine)
  LyricsTimeline      qual trecho da letra mostrar em cada instante
  CoverBuilder        baixa a capa, reduz sem serrilhado, arredonda as bordas
crewx/module/modules/render/MediaPlayer.java   só desenha o card/letra e trata o mouse
resources/assets/crewx/media/smtc.ps1      helper do Windows
```

### Nota técnica: por que o card não detectava nada no Windows (corrigido)

O helper lia comandos com `[Console]::In.ReadLineAsync()`. `Console.In` é um `SyncTextReader`, cujo
`ReadLineAsync()` na verdade é um `ReadLine()` **bloqueante**. O laço principal parava ali esperando um comando do
jogo e nunca enviava o que estava tocando (só reagia, uma vez, quando você clicava num controle). Agora o stdin é
lido numa runspace em segundo plano e os comandos chegam por uma fila; a leitura de mídia roda a cada 500 ms.
Também foi corrigido: desligar/ligar o módulo rápido deixava processos `powershell.exe` órfãos rodando, e a capa da
música anterior podia ficar na música nova.
