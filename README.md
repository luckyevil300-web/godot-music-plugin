# 🎵 GodotMusicPlugin — Plugin Nativo Android para Godot 4.x

Plugin nativo Android para o Godot Engine 4.x que adiciona:

- ✅ Reprodução de **MP3, FLAC, OGG, AAC, WAV, OPUS** (via ExoPlayer/Media3)
- ✅ **Escaneamento** de todos os arquivos de áudio do dispositivo
- ✅ Leitura de **metadados** (título, artista, álbum, duração, capa de álbum)
- ✅ **Fila ilimitada** de músicas
- ✅ Controles na **notificação** e **tela de bloqueio** (MediaSession)
- ✅ **Widget redimensionável** na tela inicial (3 tamanhos: pequeno 2×1, médio 4×1, grande 4×2)
- ✅ Modo aleatório, repetição (uma/todas), controle de volume

---

## 📦 Como instalar (sem PC — usando GitHub Mobile)

### 1. Faça fork/clone deste repositório no GitHub Mobile
- Abra o app **GitHub Mobile**
- Faça um fork deste repositório para a sua conta

### 2. O GitHub Actions compila automaticamente o `.aar`
- Cada push na branch `main` dispara o workflow `.github/workflows/build.yml`
- Vá em **Actions** → selecione o build mais recente → baixe o artifact **GodotMusicPlugin**
- Extraia o arquivo: você terá o `GodotMusicPlugin.aar`

### 3. Copie os arquivos para o seu projeto Godot
```
seu_projeto_godot/
└── addons/
    └── music_player/
        ├── plugin.gdap          ← copie de godot-addon/addons/music_player/
        ├── GodotMusicPlugin.aar ← arquivo compilado pelo GitHub Actions
        └── MusicPlayer.gd       ← copie de godot-addon/addons/music_player/
```

### 4. Configure o Godot
1. Abra seu projeto no Godot 4
2. Vá em **Projeto → Configurações do Projeto → AutoLoad**
3. Adicione `addons/music_player/MusicPlayer.gd` com o nome `MusicPlayer`
4. No exportador Android, ative o plugin **GodotMusicPlugin**
5. Adicione as permissões no AndroidManifest (veja abaixo)

### 5. Permissões no AndroidManifest do seu projeto Godot
No Godot, vá em **Projeto → Configurações de Exportação → Android** e adicione em "Custom AndroidManifest":
```xml
<uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

---

## 🎮 Como usar no GDScript

```gdscript
extends Node

func _ready():
    # Escaneie as músicas do dispositivo
    var resultado = MusicPlayer.scan()
    print("Músicas encontradas: ", resultado.count)
    
    var musicas = resultado.songs
    for musica in musicas:
        print(musica.title, " - ", musica.artist)
    
    # Define a fila de reprodução com TODAS as músicas encontradas
    MusicPlayer.set_queue_from_scan(musicas)
    
    # Conecta os sinais
    MusicPlayer.track_changed.connect(_on_musica_mudou)
    MusicPlayer.playback_state_changed.connect(_on_estado_mudou)
    MusicPlayer.scan_completed.connect(_on_scan_concluido)
    
    # Toca a primeira música
    MusicPlayer.play_at(0)

func _on_musica_mudou(index, title, artist, album, duration_ms):
    print("Tocando: %s — %s (%d ms)" % [title, artist, duration_ms])

func _on_estado_mudou(is_playing, position_ms):
    # Atualiza interface: botão play/pause, barra de progresso, etc.
    $BotaoPlayPause.text = "⏸" if is_playing else "▶"
    $BarraProgresso.value = position_ms

func _on_scan_concluido(count):
    print("Escaneamento finalizado: %d músicas" % count)

# Controles
func _on_botao_play_pause():
    MusicPlayer.play_pause()

func _on_botao_proximo():
    MusicPlayer.next()

func _on_botao_anterior():
    MusicPlayer.previous()

func _on_slider_posicao_changed(valor):
    MusicPlayer.seek(int(valor))

# Carregar capa do álbum como textura
func carregar_capa(album_art_uri: String) -> ImageTexture:
    var bytes = MusicPlayer.get_album_art(album_art_uri)
    if bytes.is_empty():
        return null
    var img = Image.new()
    img.load_png_from_buffer(bytes)
    return ImageTexture.create_from_image(img)
```

---

## 📋 API completa

| Método | Retorno | Descrição |
|--------|---------|-----------|
| `scan()` | `Dictionary` | Escaneia músicas. Retorna `{count, songs[]}` |
| `get_album_art(uri)` | `PackedByteArray` | Bytes PNG da capa |
| `set_queue(paths)` | `void` | Define fila com array de caminhos |
| `set_queue_from_scan(songs)` | `void` | Define fila direto do resultado do scan |
| `play_at(index)` | `void` | Toca a música na posição |
| `play_pause()` | `void` | Alterna reprodução/pausa |
| `next()` | `void` | Próxima música |
| `previous()` | `void` | Música anterior |
| `seek(ms)` | `void` | Pula para posição em milissegundos |
| `stop()` | `void` | Para a reprodução |
| `set_volume(0.0..1.0)` | `void` | Ajusta volume |
| `set_repeat_mode(0/1/2)` | `void` | 0=nenhum, 1=tudo, 2=uma |
| `set_shuffle(bool)` | `void` | Modo aleatório |
| `is_playing()` | `bool` | Está reproduzindo? |
| `get_position()` | `int` | Posição atual em ms |
| `get_duration()` | `int` | Duração total em ms |
| `get_current_index()` | `int` | Índice na fila |

### Sinais
| Sinal | Parâmetros | Quando dispara |
|-------|-----------|----------------|
| `track_changed` | `index, title, artist, album, duration_ms` | Música muda |
| `playback_state_changed` | `is_playing, position_ms` | A cada segundo ou pausa/play |
| `playback_completed` | — | Fila termina |
| `playback_error` | `message` | Erro de reprodução |
| `scan_completed` | `count` | Escaneamento termina |

---

## 🔧 Formatos suportados

| Formato | Extensão |
|---------|----------|
| MP3 | `.mp3` |
| FLAC | `.flac` |
| OGG Vorbis | `.ogg` |
| AAC / M4A | `.aac`, `.m4a` |
| WAV | `.wav` |
| OPUS | `.opus` |
| WMA | `.wma` |
| 3GP | `.3gp` |

---

## 🧩 Widget na tela inicial

O widget é adicionado automaticamente quando o plugin está instalado.
Para adicionar: toque longo na tela inicial → Widgets → procure **"Music Player Widget"**.

| Tamanho | Células | Conteúdo |
|---------|---------|----------|
| Pequeno | 2×1 | Capa + título + play/pause + próxima |
| Médio | 4×1 | Capa + título + artista + anterior/play/próxima |
| Grande | 4×2 | Capa grande + título + artista + controles completos |

Redimensione segurando e arrastando as bordas do widget.

---

## 🏗️ Estrutura do projeto

```
godot-music-plugin/
├── .github/workflows/build.yml     ← GitHub Actions: compila o AAR automaticamente
├── plugin/
│   ├── build.gradle                ← Dependências (ExoPlayer, Media3, etc.)
│   └── src/main/
│       ├── kotlin/com/replit/godotmusicplugin/
│       │   ├── MusicPlayerPlugin.kt    ← Ponto de entrada do plugin Godot
│       │   ├── MusicPlaybackService.kt ← Serviço foreground + MediaSession
│       │   ├── AudioScanner.kt         ← Escaneamento de arquivos de áudio
│       │   └── MusicWidgetProvider.kt  ← Widget redimensionável
│       ├── res/
│       │   ├── layout/widget_small/medium/large.xml  ← Layouts do widget
│       │   └── xml/music_widget_info.xml              ← Config do widget
│       └── AndroidManifest.xml
├── godot-addon/addons/music_player/
│   ├── plugin.gdap     ← Configuração do plugin para o Godot
│   └── MusicPlayer.gd  ← Wrapper GDScript (use como AutoLoad)
└── README.md
```

---

## 🔄 Versão do Godot

Testado com **Godot 4.3+**. Para Godot 4.6.3, a API de plugin (`GodotPlugin`, `@UsedByGodot`) é a mesma — nenhuma alteração necessária.

> **Atenção:** Certifique-se de que o AAR do Godot Engine baixado no `build.yml` corresponde à sua versão. Edite a linha `GODOT_VERSION="4.3"` no `build.yml` para `4.6.3` quando ela for lançada oficialmente.
