[Setup]
AppName=ľOpenSPIM
AppVersion=1.0.8
WizardStyle=modern
DefaultDirName={autopf64}\OpenSPIM
DefaultGroupName=OpenSPIM
UninstallDisplayIcon={app}\ľOpenSPIM.exe
Compression=lzma2
UsePreviousAppDir=no
SolidCompression=yes
OutputBaseFilename=ľOpenSPIM_setup
OutputDir=userdocs:ľOpenSPIM Output

[Files]
Source: "target\jfx\native\ľOpenSPIM\ľOpenSPIM.exe"; DestDir: "{app}"
Source: "target\jfx\native\ľOpenSPIM\*.dll"; DestDir: "{app}"
Source: "target\jfx\native\ľOpenSPIM\app\*"; DestDir: "{app}\app"; Flags: ignoreversion recursesubdirs
Source: "target\jfx\native\ľOpenSPIM\runtime\*"; DestDir: "{app}\runtime"; Flags: ignoreversion recursesubdirs

[Icons]
Name: "{group}\ľOpenSPIM"; Filename: "{app}\ľOpenSPIM.exe"

[Code]

{ ///////////////////////////////////////////////////////////////////// }
function GetUninstallString(): String;
var
  sUnInstPath: String;
  sUnInstallString: String;
begin
  sUnInstPath := ExpandConstant('Software\Microsoft\Windows\CurrentVersion\Uninstall\{#emit SetupSetting("AppId")}_is1');
  sUnInstallString := '';
  if not RegQueryStringValue(HKLM, sUnInstPath, 'UninstallString', sUnInstallString) then
    RegQueryStringValue(HKCU, sUnInstPath, 'UninstallString', sUnInstallString);
  Result := sUnInstallString;
end;


{ ///////////////////////////////////////////////////////////////////// }
function IsUpgrade(): Boolean;
begin
  Result := (GetUninstallString() <> '');
end;


{ ///////////////////////////////////////////////////////////////////// }
function UnInstallOldVersion(): Integer;
var
  sUnInstallString: String;
  iResultCode: Integer;
begin
{ Return Values: }
{ 1 - uninstall string is empty }
{ 2 - error executing the UnInstallString }
{ 3 - successfully executed the UnInstallString }
