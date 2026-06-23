import { z } from "zod";

export const loginSchema = z.object({
  password: z.string().min(1, "请输入密码"),
  redirectTo: z.string().optional(),
});

export type LoginInput = z.infer<typeof loginSchema>;
