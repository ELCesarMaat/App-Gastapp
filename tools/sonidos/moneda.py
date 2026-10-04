"""
Genera el sonido de notificacion de Gastapp.

Cada variante son una o dos notas de senos (sin la onda cuadrada de los videojuegos),
con parciales opcionales que dan brillo metalico, un ataque suave y caida exponencial.

Uso (desde la raiz del repo):
    python tools/sonidos/moneda.py                 # la variante elegida -> res/raw
    python tools/sonidos/moneda.py gota            # otra variante -> res/raw
    python tools/sonidos/moneda.py --todas <dir>   # todas las variantes en <dir>, para oirlas

Escribe Gastapp.Android/app/src/main/res/raw/notificacion_moneda.wav (PCM 16 bits mono,
44.1 kHz). Android no deja cambiar el sonido de un canal ya creado: si se cambia el
archivo hay que subir VERSION en NotificationChannels (AppNotifier.kt).
"""
import os
import sys
import wave

import numpy as np

RATE = 44_100

# Cada variante: notas (frecuencia, inicio s, caida s, volumen), parciales (multiplo,
# volumen), ataque (s), duracion (s) y pico (dBFS).
VARIANTS = {
    # La primera version: brillante, dos notas y brillo de campana. Al usuario no le gusto.
    "moneda": dict(
        notes=[(987.77, 0.000, 0.060, 0.55), (1318.51, 0.055, 0.170, 1.00)],
        partials=[(1.0, 1.00), (2.76, 0.16), (5.40, 0.05)],
        attack=0.003, duration=0.45, peak=-3,
    ),
    # Una sola nota limpia y redonda, como una gota: lo mas discreto.
    "gota": dict(
        notes=[(880.00, 0.000, 0.110, 1.00)],
        partials=[(1.0, 1.00), (2.0, 0.06)],
        attack=0.008, duration=0.40, peak=-6,
    ),
    # Dos notas suaves y graves (mi -> la), sin brillo metalico.
    "suave": dict(
        notes=[(659.26, 0.000, 0.050, 0.60), (880.00, 0.070, 0.150, 1.00)],
        partials=[(1.0, 1.00), (2.0, 0.05)],
        attack=0.010, duration=0.50, peak=-6,
    ),
    # Moneda sutil: dos notas medias (sol -> do) con un toque metalico muy bajo.
    "moneda_sutil": dict(
        notes=[(783.99, 0.000, 0.045, 0.55), (1046.50, 0.060, 0.130, 1.00)],
        partials=[(1.0, 1.00), (2.76, 0.04)],
        attack=0.006, duration=0.45, peak=-6,
    ),
}

# La que usa la app (elegida por el usuario entre las opciones; "moneda" no le gusto).
CHOSEN = "cobro"

# ------------------------------------------------------------------ sonidos de dinero
# Una moneda es un disco delgado: suena con modos inarmonicos (no multiplos enteros) que
# se apagan rapido, mas un golpecito de ruido al chocar. Para que sea sutil se filtra lo
# muy agudo y se deja el pico bajo.

COIN_MODES = [(1.00, 1.00), (1.72, 0.55), (2.33, 0.40), (3.03, 0.22), (4.10, 0.12)]


def strike(t, start, base, decay, gain, modes=COIN_MODES, noise=0.15, rng=None):
    """Un golpe de moneda: modos que se apagan (los agudos antes) y un chasquido corto."""
    rng = rng or np.random.default_rng(7)
    local = t - start
    active = local >= 0
    lt = local[active]
    out = np.zeros_like(t)
    attack = np.minimum(lt / 0.0015, 1.0)
    for ratio, amp in modes:
        out[active] += amp * attack * np.exp(-lt / (decay / ratio ** 0.7)) * np.sin(2 * np.pi * base * ratio * lt)
    if noise:
        burst = rng.standard_normal(lt.size) * np.exp(-lt / 0.004)
        out[active] += noise * bandpass(burst, base * 1.5, base * 2.5)
    return gain * out


def bell(t, start, freq, decay, gain, partials=((1.0, 1.0), (2.0, 0.12), (3.01, 0.04)), attack=0.004):
    """Una nota de campanita limpia."""
    local = t - start
    active = local >= 0
    lt = local[active]
    out = np.zeros_like(t)
    env = np.minimum(lt / attack, 1.0) * np.exp(-lt / decay)
    for ratio, amp in partials:
        out[active] += amp * env * np.exp(-lt * (ratio - 1) * 4) * np.sin(2 * np.pi * freq * ratio * lt)
    return gain * out


def bandpass(x, low, high):
    spectrum = np.fft.rfft(x)
    freqs = np.fft.rfftfreq(x.size, 1 / RATE)
    center, width = (low + high) / 2, (high - low) / 2
    spectrum *= np.exp(-(((freqs - center) / width) ** 2))
    return np.fft.irfft(spectrum, x.size)


def lowpass(x, cutoff):
    """Suaviza lo muy agudo (caida gradual, sin cortar en seco)."""
    spectrum = np.fft.rfft(x)
    freqs = np.fft.rfftfreq(x.size, 1 / RATE)
    spectrum /= np.sqrt(1 + (freqs / cutoff) ** 4)
    return np.fft.irfft(spectrum, x.size)


def finish(signal, peak, cutoff):
    signal = lowpass(signal, cutoff)
    fade = int(RATE * 0.03)
    signal[-fade:] *= np.linspace(1.0, 0.0, fade)
    return signal / np.max(np.abs(signal)) * 10 ** (peak / 20)


def timeline(duration):
    return np.arange(int(RATE * duration)) / RATE


def tintineo():
    """Dos monedas que se tocan: clink-clink, el segundo mas bajito."""
    t = timeline(0.45)
    s = strike(t, 0.000, 2100, 0.090, 1.0) + strike(t, 0.085, 2350, 0.120, 0.55, rng=np.random.default_rng(3))
    return finish(s, -8, 5000)


def monedas():
    """Unas monedas que caen y se acomodan: tres toques que se van juntando."""
    t = timeline(0.55)
    s = (strike(t, 0.000, 1900, 0.080, 1.0)
         + strike(t, 0.110, 2250, 0.070, 0.6, rng=np.random.default_rng(1))
         + strike(t, 0.175, 2050, 0.090, 0.4, rng=np.random.default_rng(2)))
    return finish(s, -9, 4500)


def cobro():
    """Un "ka-ching" moderno y discreto: un clic de moneda y una campanita de dos notas."""
    t = timeline(0.70)
    s = (strike(t, 0.000, 2400, 0.040, 0.45, noise=0.25)
         + bell(t, 0.050, 1046.50, 0.18, 0.8)
         + bell(t, 0.120, 1318.51, 0.30, 1.0))
    return finish(s, -8, 6000)


def pago_listo():
    """Confirmacion de pago, estilo cartera digital: dos notas redondas con un brillo minimo."""
    t = timeline(0.60)
    soft = ((1.0, 1.0), (2.0, 0.08), (2.76, 0.03))
    s = bell(t, 0.000, 880.00, 0.12, 0.7, soft, attack=0.008) + bell(t, 0.090, 1174.66, 0.22, 1.0, soft, attack=0.008)
    return finish(s, -7, 5000)


def alcancia():
    """Una moneda que cae en una alcancia: un clink apagado con un cuerpo mas grave."""
    t = timeline(0.45)
    body = [(1.00, 1.00), (1.59, 0.45), (2.14, 0.25)]
    s = strike(t, 0.000, 1700, 0.070, 1.0, noise=0.2) + strike(t, 0.004, 620, 0.090, 0.5, modes=body, noise=0)
    return finish(s, -8, 4000)


MONEY = {"tintineo": tintineo, "monedas": monedas, "cobro": cobro, "pago_listo": pago_listo, "alcancia": alcancia}


def render(spec: dict) -> np.ndarray:
    t = np.arange(int(RATE * spec["duration"])) / RATE
    signal = np.zeros_like(t)
    for freq, start, decay, gain in spec["notes"]:
        local = t - start
        active = local >= 0
        lt = local[active]
        envelope = np.minimum(lt / spec["attack"], 1.0) * np.exp(-lt / decay)
        for ratio, amp in spec["partials"]:
            # Los parciales altos se apagan antes, como en un metal real.
            partial_env = envelope * np.exp(-lt * (ratio - 1.0) * 6.0)
            signal[active] += gain * amp * partial_env * np.sin(2 * np.pi * freq * ratio * lt)

    # Desvanecido final de 30 ms para que no truene al cortar.
    fade = int(RATE * 0.03)
    signal[-fade:] *= np.linspace(1.0, 0.0, fade)
    return signal / np.max(np.abs(signal)) * 10 ** (spec["peak"] / 20)


def write(signal: np.ndarray, target: str) -> None:
    os.makedirs(os.path.dirname(target), exist_ok=True)
    with wave.open(target, "wb") as wav:
        wav.setnchannels(1)
        wav.setsampwidth(2)
        wav.setframerate(RATE)
        wav.writeframes((signal * 32767).astype("<i2").tobytes())
    print(f"{target} ({len(signal) / RATE:.2f} s, {os.path.getsize(target) // 1024} KB)")


def main() -> None:
    args = sys.argv[1:]
    if args and args[0] == "--todas":
        folder = args[1] if len(args) > 1 else "."
        for name, spec in VARIANTS.items():
            write(render(spec), os.path.join(folder, f"{name}.wav"))
        for name, make in MONEY.items():
            write(make(), os.path.join(folder, f"{name}.wav"))
        return

    name = args[0] if args else CHOSEN
    root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    target = os.path.join(root, "Gastapp.Android", "app", "src", "main", "res", "raw", "notificacion_moneda.wav")
    write(MONEY[name]() if name in MONEY else render(VARIANTS[name]), target)


if __name__ == "__main__":
    main()
