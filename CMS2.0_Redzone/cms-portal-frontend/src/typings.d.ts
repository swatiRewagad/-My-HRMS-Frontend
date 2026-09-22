declare module 'html2canvas' {
  interface Options {
    scale?: number;
    useCORS?: boolean;
    logging?: boolean;
    backgroundColor?: string;
    width?: number;
    height?: number;
  }
  function html2canvas(element: HTMLElement, options?: Options): Promise<HTMLCanvasElement>;
  export default html2canvas;
}
