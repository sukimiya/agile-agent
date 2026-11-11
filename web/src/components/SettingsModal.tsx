// src/components/SettingsModal.tsx
import { useEffect, useState } from "react";
import KnowledgeUploadPanel from "./settings/KnowledgeUploadPanel";
import ReviewListPanel from "./settings/ReviewListPanel";
import HintManagementPanel from "./settings/HintManagementPanel";
import BalancePanel from "./settings/BalancePanel";
import LlmConfigPanel from "./settings/LlmConfigPanel";
import LlmPresetPanel from "./settings/LlmPresetPanel";
import AppearancePanel from "./settings/AppearancePanel";

export type MenuKey =
  | "appearance"
  | "knowledge-upload"
  | "api-doc-upload"
  | "review-list"
  | "hint-management"
  | "llm-config"
  | "balance"
  | "llm-presets";

interface Props {
  isOpen: boolean;
  onClose: () => void;
  platformAdmin: boolean;
  tenantAdmin: boolean;
  initialMenu?: MenuKey;
}

function CloseIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <line x1="18" y1="6" x2="6" y2="18" />
      <line x1="6" y1="6" x2="18" y2="18" />
    </svg>
  );
}

export default function SettingsModal({ isOpen, onClose, platformAdmin, tenantAdmin, initialMenu }: Props) {
  const [menu, setMenu] = useState<MenuKey>(initialMenu ?? "knowledge-upload");

  useEffect(() => {
    if (isOpen) setMenu(initialMenu ?? "knowledge-upload");
  }, [isOpen, initialMenu]);

  useEffect(() => {
    if (!isOpen) return;
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  const canManageTenant = platformAdmin || tenantAdmin;
  const menuItems: { key: MenuKey; label: string }[] = [
    { key: "appearance", label: "外观" },
    { key: "knowledge-upload", label: "知识库上传" },
    ...(canManageTenant ? [{ key: "api-doc-upload" as MenuKey, label: "API文档上传" }] : []),
    { key: "review-list", label: "文件上传审核列表" },
    ...(platformAdmin ? [{ key: "hint-management" as MenuKey, label: "提示语管理" }] : []),
    { key: "llm-config", label: "LLM 设置" },
    ...(canManageTenant ? [{ key: "balance" as MenuKey, label: "我的余额" }] : []),
    ...(platformAdmin ? [{ key: "llm-presets" as MenuKey, label: "模型预置管理" }] : []),
  ];

  return (
    <div
      className="fixed inset-0 z-50 bg-black/40 flex items-center justify-center"
      onClick={onClose}
    >
      <div
        className="w-[90vw] max-w-6xl h-[85vh] bg-surface rounded-2xl shadow-soft border border-stone-200 flex overflow-hidden"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="w-44 shrink-0 border-r border-stone-200 bg-stone-50 p-2 space-y-1">
          {menuItems.map((item) => (
            <button
              key={item.key}
              type="button"
              onClick={() => setMenu(item.key)}
              className={`w-full text-left px-3 py-2 rounded-lg text-sm transition-colors cursor-pointer
                ${menu === item.key ? "bg-frozen-water-mountain-lake text-medium-violet-red" : "hover:bg-frozen-water text-stone-600"}`}
            >
              {item.label}
            </button>
          ))}
        </div>

        <div className="flex-1 min-w-0 p-5 overflow-y-auto relative">
          <button
            type="button"
            onClick={onClose}
            aria-label="关闭"
            className="absolute top-4 right-4 text-stone-400 hover:text-stone-700 cursor-pointer p-1 rounded hover:bg-stone-100 transition-colors"
          >
            <CloseIcon />
          </button>

          {menu === "appearance" && <AppearancePanel />}
          {menu === "knowledge-upload" && <KnowledgeUploadPanel heading="知识库上传" />}
          {menu === "api-doc-upload" && canManageTenant && <KnowledgeUploadPanel heading="API文档上传" />}
          {menu === "review-list" && <ReviewListPanel />}
          {menu === "hint-management" && platformAdmin && <HintManagementPanel />}
          {menu === "llm-config" && <LlmConfigPanel canEdit={tenantAdmin} />}
          {menu === "balance" && canManageTenant && <BalancePanel onGoToLlmConfig={() => setMenu("llm-config")} />}
          {menu === "llm-presets" && platformAdmin && <LlmPresetPanel />}
        </div>
      </div>
    </div>
  );
}
