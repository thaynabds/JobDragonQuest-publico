import { useCallback, useEffect, useRef, useState } from "react";

export interface AsyncState<T> {
  data: T | null;
  error: unknown;
  loading: boolean;
  reload: () => void;
}

/**
 * Carrega dados assíncronos e descarta respostas atrasadas: se a dependência mudar no meio do caminho, só a última
 * resposta vale (evita mostrar dados do filtro anterior).
 */
export function useAsync<T>(load: () => Promise<T>, deps: unknown[]): AsyncState<T> {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(true);
  const [tick, setTick] = useState(0);
  const latest = useRef(0);

  // eslint-disable-next-line react-hooks/exhaustive-deps
  const run = useCallback(load, deps);

  useEffect(() => {
    const call = ++latest.current;
    setLoading(true);
    run()
      .then((value) => {
        if (call === latest.current) {
          setData(value);
          setError(null);
        }
      })
      .catch((err: unknown) => {
        if (call === latest.current) {
          setError(err);
        }
      })
      .finally(() => {
        if (call === latest.current) {
          setLoading(false);
        }
      });
  }, [run, tick]);

  const reload = useCallback(() => setTick((value) => value + 1), []);
  return { data, error, loading, reload };
}
