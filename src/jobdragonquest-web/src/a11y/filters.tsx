// Filtros de cor do Congo Grey Parrot (ui/color_filter.py), aplicados por SVG feColorMatrix.
// Protanopia, deuteranopia e tritanopia: modo Correction do Daltonizer do Android, nível máximo
// (https://android.googlesource.com/platform/frameworks/native/+/refs/heads/master/services/surfaceflinger/Effects/Daltonizer.cpp).
// As contas são feitas em sRGB, como no Congo (numpy sobre os valores 0..1 da tela).
// Monocromia: luminância da Rec. ITU-R BT.709. Inverter e inversão inteligente usam filtros CSS (veja styles.css).

type Matrix3 = readonly [readonly [number, number, number], readonly [number, number, number], readonly [number, number, number]];

export const COLOR_MATRICES: Record<"protanopia" | "deuteranopia" | "tritanopia" | "grayscale", Matrix3> = {
  protanopia: [
    [0.46111, 0.53889, 0.0],
    [0.37754, 0.62246, 0.0],
    [0.30974, -0.30974, 1.0],
  ],
  deuteranopia: [
    [-0.01672, 1.01672, 0.0],
    [0.0753, 0.9247, 0.0],
    [-0.36845, 0.36845, 1.0],
  ],
  tritanopia: [
    [1.0, -1.15102, 1.15102],
    [0.0, 0.11263, 0.88737],
    [0.0, 0.14975, 0.85025],
  ],
  grayscale: [
    [0.2126, 0.7152, 0.0722],
    [0.2126, 0.7152, 0.0722],
    [0.2126, 0.7152, 0.0722],
  ],
};

/** Matriz 4x5 do feColorMatrix (linhas R, G, B, A), sem deslocamento. */
export function feValues(matrix: Matrix3): string {
  const rows = matrix.map((row) => [...row, 0, 0].join(" "));
  return [...rows, "0 0 0 1 0"].join(" ");
}

/** SVG invisível com os filtros, referenciados no CSS por url(#a11y-…). */
export function A11yFilters() {
  return (
    <svg className="a11y-svg-filters" aria-hidden="true" focusable="false" width="0" height="0">
      <defs>
        {Object.entries(COLOR_MATRICES).map(([name, matrix]) => (
          <filter key={name} id={`a11y-${name}`} colorInterpolationFilters="sRGB">
            <feColorMatrix type="matrix" values={feValues(matrix)} />
          </filter>
        ))}
      </defs>
    </svg>
  );
}
