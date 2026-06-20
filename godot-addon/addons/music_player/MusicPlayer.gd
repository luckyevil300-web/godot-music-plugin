## MusicPlayer.gd
## Wrapper GDScript para o GodotMusicPlugin nativo Android.
##
## Como usar:
##   1. Adicione este script como AutoLoad em Projeto > Configurações do Projeto > AutoLoad
##      Nome sugerido: MusicPlayer
##   2. Em qualquer cena, use os métodos abaixo.
##
## Exemplo rápido:
##   MusicPlayer.scan()
##   MusicPlayer.set_queue(["file:///sdcard/Music/musica.mp3"])
##   MusicPlayer.play_at(0)

extends Node

signal track_changed(index: int, title: String, artist: String, album: String, duration_ms: int)
signal playback_state_changed(is_playing: bool, position_ms: int)
signal playback_completed()
signal playback_error(message: String)
signal scan_completed(count: int)

## Resultado do último escaneamento
var last_scan_result: Dictionary = {}
var songs: Array = []

var _plugin = null

func _ready() -> void:
	if Engine.has_singleton("GodotMusicPlugin"):
		_plugin = Engine.get_singleton("GodotMusicPlugin")
		_plugin.connect("track_changed", _on_track_changed)
		_plugin.connect("playback_state_changed", _on_playback_state_changed)
		_plugin.connect("playback_completed", _on_playback_completed)
		_plugin.connect("playback_error", _on_playback_error)
		_plugin.connect("scan_completed", _on_scan_completed)
		print("[MusicPlayer] Plugin carregado com sucesso!")
	else:
		push_warning("[MusicPlayer] GodotMusicPlugin não encontrado. Rode no Android com o plugin instalado.")

## ─────────────────────────────────────────────
## Escaneamento
## ─────────────────────────────────────────────

## Escaneia todos os arquivos de áudio no dispositivo.
## Retorna um Dictionary com "count" e "songs" (Array de Dicts).
## Também emite o sinal scan_completed(count).
func scan() -> Dictionary:
	if _plugin == null:
		push_warning("[MusicPlayer] Plugin não disponível.")
		return {}
	last_scan_result = _plugin.scanAudioFiles()
	songs = last_scan_result.get("songs", [])
	return last_scan_result

## Retorna os bytes PNG da capa de álbum dado um albumArtUri do scan.
## Útil para criar uma ImageTexture no Godot:
##   var bytes = MusicPlayer.get_album_art(song.albumArtUri)
##   var img = Image.new()
##   img.load_png_from_buffer(bytes)
##   var tex = ImageTexture.create_from_image(img)
func get_album_art(album_art_uri: String) -> PackedByteArray:
	if _plugin == null:
		return PackedByteArray()
	return _plugin.getAlbumArtBytes(album_art_uri)

## ─────────────────────────────────────────────
## Fila de reprodução
## ─────────────────────────────────────────────

## Define a fila de reprodução com uma lista de caminhos de arquivo.
## Exemplo: set_queue(["/sdcard/Music/song.mp3", "/sdcard/Music/song2.flac"])
func set_queue(paths: Array) -> void:
	if _plugin == null:
		return
	_plugin.setQueue(PackedStringArray(paths))

## Define a fila usando os resultados do scan diretamente.
func set_queue_from_scan(track_array: Array) -> void:
	var paths: Array = []
	for song in track_array:
		paths.append(song.get("path", ""))
	set_queue(paths)

## ─────────────────────────────────────────────
## Controles de reprodução
## ─────────────────────────────────────────────

func play_at(index: int) -> void:
	if _plugin:
		_plugin.playAtIndex(index)

func play_pause() -> void:
	if _plugin:
		_plugin.playPause()

func next() -> void:
	if _plugin:
		_plugin.skipToNext()

func previous() -> void:
	if _plugin:
		_plugin.skipToPrevious()

func seek(position_ms: int) -> void:
	if _plugin:
		_plugin.seekTo(position_ms)

func stop() -> void:
	if _plugin:
		_plugin.stop()

func set_volume(volume: float) -> void:
	if _plugin:
		_plugin.setVolume(volume)

## repeat_mode: 0 = sem repetição, 1 = repetir tudo, 2 = repetir uma
func set_repeat_mode(repeat_mode: int) -> void:
	if _plugin:
		_plugin.setRepeatMode(repeat_mode)

func set_shuffle(enabled: bool) -> void:
	if _plugin:
		_plugin.setShuffleMode(enabled)

## ─────────────────────────────────────────────
## Estado
## ─────────────────────────────────────────────

func is_playing() -> bool:
	if _plugin:
		return _plugin.isPlaying()
	return false

func get_position() -> int:
	if _plugin:
		return _plugin.getCurrentPosition()
	return 0

func get_duration() -> int:
	if _plugin:
		return _plugin.getDuration()
	return 0

func get_current_index() -> int:
	if _plugin:
		return _plugin.getCurrentIndex()
	return 0

## ─────────────────────────────────────────────
## Callbacks internos
## ─────────────────────────────────────────────

func _on_track_changed(index: int, title: String, artist: String, album: String, duration: int) -> void:
	emit_signal("track_changed", index, title, artist, album, duration)

func _on_playback_state_changed(is_playing: bool, position: int) -> void:
	emit_signal("playback_state_changed", is_playing, position)

func _on_playback_completed() -> void:
	emit_signal("playback_completed")

func _on_playback_error(message: String) -> void:
	emit_signal("playback_error", message)
	push_error("[MusicPlayer] Erro: " + message)

func _on_scan_completed(count: int) -> void:
	emit_signal("scan_completed", count)
	print("[MusicPlayer] Escaneamento concluído: %d faixas" % count)
