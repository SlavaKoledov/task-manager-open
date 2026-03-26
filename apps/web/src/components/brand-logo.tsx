type BrandLogoProps = {
  className?: string;
};

export function BrandLogo({ className }: BrandLogoProps) {
  return <img src="/task-manager-logo.svg" alt="Task Manager logo" className={className} />;
}
