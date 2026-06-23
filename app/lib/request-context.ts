import { createContext } from "react-router";

export type WaitUntil = (promise: Promise<unknown>) => void;

export const waitUntilContext = createContext<WaitUntil>((promise) => {
  void promise;
});
