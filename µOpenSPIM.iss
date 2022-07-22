[Setup]
AppName=킣penSPIM
AppVersion=1.0.8
WizardStyle=modern
DefaultDirName={autopf64}\OpenSPIM
DefaultGroupName=OpenSPIM
UninstallDisplayIcon={app}\킣penSPIM.exe
Compression=lzma2
UsePreviousAppDir=no
SolidCompression=yes
OutputBaseFilename=킣penSPIM_setup
OutputDir=userdocs:킣penSPIM Output

[Files]
Source: "target\jfx\native\킣penSPIM\킣penSPIM.exe"; DestDir: "{app}"
Source: "target\jfx\native\킣penSPIM\*.dll"; DestDir: "{app}"
Source: "target\jfx\native\킣penSPIM\app\*"; DestDir: "{app}\app"; Flags: ignoreversion recursesubdirs
Source: "target\jfx\native\킣penSPIM\runtime\*"; DestDir: "{app}\runtime"; Flags: ignoreversion recursesubdirs

[Icons]
Name: "{group}\킣penSPIM"; Filename: "{app}\킣penSPIM.exe"

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
