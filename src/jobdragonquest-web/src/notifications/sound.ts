// Som curto gerado no próprio navegador (Web Audio), sem arquivo externo. Sem permissão de áudio, fica em silêncio.
let context: AudioContext | null = null;

export function playChime(): void {
  try {
    const AudioCtor = window.AudioContext ?? (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
    if (!AudioCtor) {
      return;
    }
    context ??= new AudioCtor();
    const now = context.currentTime;
    [880, 1320].forEach((frequency, index) => {
      const oscillator = context!.createOscillator();
      const gain = context!.createGain();
      oscillator.type = "sine";
      oscillator.frequency.value = frequency;
      gain.gain.setValueAtTime(0.0001, now + index * 0.18);
      gain.gain.exponentialRampToValueAtTime(0.25, now + index * 0.18 + 0.02);
      gain.gain.exponentialRampToValueAtTime(0.0001, now + index * 0.18 + 0.16);
      oscillator.connect(gain).connect(context!.destination);
      oscillator.start(now + index * 0.18);
      oscillator.stop(now + index * 0.18 + 0.17);
    });
  } catch {
    // Navegador bloqueou o áudio antes de um clique: sem som desta vez.
  }
}
