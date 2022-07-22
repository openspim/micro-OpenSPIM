[Setup]
AppName=�OpenSPIM
AppVersion=1.0.8
WizardStyle=modern
DefaultDirName={autopf64}\OpenSPIM
DefaultGroupName=OpenSPIM
UninstallDisplayIcon={app}\�OpenSPIM.exe
Compression=lzma2
UsePreviousAppDir=no
SolidCompression=yes
OutputBaseFilename=�OpenSPIM_setup
OutputDir=userdocs:�OpenSPIM Output

[Files]
Source: "target\jfx\native\�OpenSPIM\�OpenSPIM.exe"; DestDir: "{app}"
Source: "target\jfx\native\�OpenSPIM\*.dll"; DestDir: "{app}"
Source: "target\jfx\native\�OpenSPIM\app\*"; DestDir: "{app}\app"; Flags: ignoreversion recursesubdirs
Source: "target\jfx\native\�OpenSPIM\runtime\*"; DestDir: "{app}\runtime"; Flags: ignoreversion recursesubdirs

[Icons]
Name: "{group}\�OpenSPIM"; Filename: "{app}\�OpenSPIM.exe"

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

  { default return value }
  Result := 0;

  { get the uninstall string of the old app }
  sUnInstallString := GetUninstallString();
  if sUnInstallString <> '' then begin
    sUnInstallString := RemoveQuotes(sUnInstallString);
    if Exec(sUnInstallString, '/SILENT /NORESTART /SUPPRESSMSGBOXES','', SW_HIDE, ewWaitUntilTerminated, iResultCode) then
      Result := 3
    else
      Result := 2;
  end else
    Result := 1;
end;

{ ///////////////////////////////////////////////////////////////////// }
procedure CurStepChanged(CurStep: TSetupStep);
begin
  if (CurStep=ssInstall) then
  begin
    if (IsUpgrade()) then
    begin
      UnInstallOldVersion();
    end;
  end;
end;