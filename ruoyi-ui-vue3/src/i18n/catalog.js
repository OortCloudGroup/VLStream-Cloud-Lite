const localeOrder = ['zh-CN', 'en-US', 'es-MX', 'ar', 'de-DE', 'fr-FR', 'ja-JP', 'pt-BR', 'ru-RU', 'ko-KR', 'id-ID', 'tr-TR']

const namedMessages = {
  'zh-CN': {
    common: { confirm: '确定', cancel: '取消', prompt: '提示', systemPrompt: '系统提示' },
    language: { label: '语言' },
    account: { profile: '个人中心', layout: '布局设置', logout: '退出登录', logoutConfirm: '确定注销并退出系统吗？' },
    login: {
      account: '账号', password: '密码', captcha: '验证码', remember: '记住密码', signIn: '登录', signingIn: '登录中...',
      register: '立即注册', loadingMode: '正在读取登录方式...', reload: '重新加载', ssoEnabled: '当前启用统一身份认证',
      ssoTip: '请从统一平台进入本系统。', unsupportedMode: '后端返回了不支持的登录方式', localInitFailed: '本地登录初始化失败，请稍后重试',
      modeLoadFailed: '无法读取后端登录方式，请检查后端服务', accountRequired: '请输入您的账号', passwordRequired: '请输入您的密码',
      captchaRequired: '请输入验证码', success: '登录成功'
    },
    tags: { refresh: '刷新页面', close: '关闭当前', closeOthers: '关闭其他', closeLeft: '关闭左侧', closeRight: '关闭右侧', closeAll: '全部关闭' },
    request: {
      sessionExpired: '登录状态已过期，您可以继续留在该页面，或者重新登录', relogin: '重新登录', invalidSession: '无效的会话，或者会话已过期，请重新登录。',
      network: '后端接口连接异常', timeout: '系统接口请求超时', httpError: '系统接口 {status} 异常', downloading: '正在下载数据，请稍候', downloadError: '下载文件出现错误，请联系管理员！', duplicate: '数据正在处理，请勿重复提交'
    }
  },
  'en-US': {
    common: { confirm: 'Confirm', cancel: 'Cancel', prompt: 'Notice', systemPrompt: 'System notice' }, language: { label: 'Language' },
    account: { profile: 'Profile', layout: 'Layout settings', logout: 'Sign out', logoutConfirm: 'Are you sure you want to sign out?' },
    login: { account: 'Account', password: 'Password', captcha: 'Verification code', remember: 'Remember password', signIn: 'Sign in', signingIn: 'Signing in...', register: 'Register now', loadingMode: 'Loading sign-in method...', reload: 'Reload', ssoEnabled: 'Single sign-on is enabled', ssoTip: 'Open this system from the unified platform.', unsupportedMode: 'The server returned an unsupported sign-in method', localInitFailed: 'Local sign-in initialization failed. Try again later.', modeLoadFailed: 'Unable to load the sign-in method. Check the backend service.', accountRequired: 'Enter your account', passwordRequired: 'Enter your password', captchaRequired: 'Enter the verification code', success: 'Signed in successfully' },
    tags: { refresh: 'Refresh', close: 'Close', closeOthers: 'Close others', closeLeft: 'Close to the left', closeRight: 'Close to the right', closeAll: 'Close all' },
    request: { sessionExpired: 'Your session has expired. You can stay on this page or sign in again.', relogin: 'Sign in again', invalidSession: 'The session is invalid or has expired. Sign in again.', network: 'Unable to connect to the backend service', timeout: 'The request timed out', httpError: 'Request failed with status {status}', downloading: 'Downloading. Please wait...', downloadError: 'The download failed. Contact the administrator.', duplicate: 'The request is being processed. Do not submit it again.' }
  },
  'es-MX': {
    common: { confirm: 'Confirmar', cancel: 'Cancelar', prompt: 'Aviso', systemPrompt: 'Aviso del sistema' }, language: { label: 'Idioma' },
    account: { profile: 'Perfil', layout: 'Configuración de diseño', logout: 'Cerrar sesión', logoutConfirm: '¿Quieres cerrar la sesión?' },
    login: { account: 'Cuenta', password: 'Contraseña', captcha: 'Código de verificación', remember: 'Recordar contraseña', signIn: 'Iniciar sesión', signingIn: 'Iniciando sesión...', register: 'Registrarse', loadingMode: 'Cargando método de acceso...', reload: 'Volver a cargar', ssoEnabled: 'El inicio de sesión único está habilitado', ssoTip: 'Abre este sistema desde la plataforma unificada.', unsupportedMode: 'El servidor devolvió un método de acceso no compatible', localInitFailed: 'No se pudo iniciar el acceso local. Inténtalo más tarde.', modeLoadFailed: 'No se pudo cargar el método de acceso. Revisa el servicio backend.', accountRequired: 'Ingresa tu cuenta', passwordRequired: 'Ingresa tu contraseña', captchaRequired: 'Ingresa el código de verificación', success: 'Inicio de sesión correcto' },
    tags: { refresh: 'Actualizar', close: 'Cerrar', closeOthers: 'Cerrar los demás', closeLeft: 'Cerrar a la izquierda', closeRight: 'Cerrar a la derecha', closeAll: 'Cerrar todo' },
    request: { sessionExpired: 'Tu sesión venció. Puedes permanecer en esta página o iniciar sesión de nuevo.', relogin: 'Iniciar de nuevo', invalidSession: 'La sesión no es válida o venció. Inicia sesión de nuevo.', network: 'No se pudo conectar con el servicio backend', timeout: 'La solicitud agotó el tiempo de espera', httpError: 'La solicitud falló con el estado {status}', downloading: 'Descargando. Espera...', downloadError: 'No se pudo descargar el archivo. Contacta al administrador.', duplicate: 'La solicitud se está procesando. No la envíes de nuevo.' }
  },
  'ar': {
    common: { confirm: 'تأكيد', cancel: 'إلغاء', prompt: 'تنبيه', systemPrompt: 'تنبيه النظام' }, language: { label: 'اللغة' },
    account: { profile: 'الملف الشخصي', layout: 'إعدادات التخطيط', logout: 'تسجيل الخروج', logoutConfirm: 'هل تريد تسجيل الخروج؟' },
    login: { account: 'الحساب', password: 'كلمة المرور', captcha: 'رمز التحقق', remember: 'تذكر كلمة المرور', signIn: 'تسجيل الدخول', signingIn: 'جارٍ تسجيل الدخول...', register: 'التسجيل الآن', loadingMode: 'جارٍ تحميل طريقة تسجيل الدخول...', reload: 'إعادة التحميل', ssoEnabled: 'تم تمكين تسجيل الدخول الموحد', ssoTip: 'افتح هذا النظام من المنصة الموحدة.', unsupportedMode: 'أعاد الخادم طريقة تسجيل دخول غير مدعومة', localInitFailed: 'تعذرت تهيئة تسجيل الدخول المحلي. حاول لاحقًا.', modeLoadFailed: 'تعذر تحميل طريقة تسجيل الدخول. تحقق من خدمة الخادم.', accountRequired: 'أدخل حسابك', passwordRequired: 'أدخل كلمة المرور', captchaRequired: 'أدخل رمز التحقق', success: 'تم تسجيل الدخول بنجاح' },
    tags: { refresh: 'تحديث', close: 'إغلاق', closeOthers: 'إغلاق علامات التبويب الأخرى', closeLeft: 'إغلاق ما على اليسار', closeRight: 'إغلاق ما على اليمين', closeAll: 'إغلاق الكل' },
    request: { sessionExpired: 'انتهت صلاحية جلستك. يمكنك البقاء في هذه الصفحة أو تسجيل الدخول مجددًا.', relogin: 'تسجيل الدخول مجددًا', invalidSession: 'الجلسة غير صالحة أو منتهية. سجّل الدخول مجددًا.', network: 'تعذر الاتصال بخدمة الخادم', timeout: 'انتهت مهلة الطلب', httpError: 'فشل الطلب بالحالة {status}', downloading: 'جارٍ التنزيل. يرجى الانتظار...', downloadError: 'فشل تنزيل الملف. تواصل مع المسؤول.', duplicate: 'الطلب قيد المعالجة. لا ترسله مرة أخرى.' }
  },
  'de-DE': {
    common: { confirm: 'Bestätigen', cancel: 'Abbrechen', prompt: 'Hinweis', systemPrompt: 'Systemhinweis' }, language: { label: 'Sprache' }, account: { profile: 'Profil', layout: 'Layouteinstellungen', logout: 'Abmelden', logoutConfirm: 'Möchten Sie sich wirklich abmelden?' },
    login: { account: 'Konto', password: 'Passwort', captcha: 'Bestätigungscode', remember: 'Passwort merken', signIn: 'Anmelden', signingIn: 'Anmeldung läuft...', register: 'Jetzt registrieren', loadingMode: 'Anmeldemethode wird geladen...', reload: 'Neu laden', ssoEnabled: 'Single Sign-on ist aktiviert', ssoTip: 'Öffnen Sie dieses System über die zentrale Plattform.', unsupportedMode: 'Der Server hat eine nicht unterstützte Anmeldemethode zurückgegeben', localInitFailed: 'Die lokale Anmeldung konnte nicht initialisiert werden. Versuchen Sie es später erneut.', modeLoadFailed: 'Die Anmeldemethode konnte nicht geladen werden. Prüfen Sie den Backend-Dienst.', accountRequired: 'Konto eingeben', passwordRequired: 'Passwort eingeben', captchaRequired: 'Bestätigungscode eingeben', success: 'Erfolgreich angemeldet' },
    tags: { refresh: 'Aktualisieren', close: 'Schließen', closeOthers: 'Andere schließen', closeLeft: 'Links schließen', closeRight: 'Rechts schließen', closeAll: 'Alle schließen' },
    request: { sessionExpired: 'Ihre Sitzung ist abgelaufen. Sie können auf dieser Seite bleiben oder sich erneut anmelden.', relogin: 'Erneut anmelden', invalidSession: 'Die Sitzung ist ungültig oder abgelaufen. Melden Sie sich erneut an.', network: 'Keine Verbindung zum Backend-Dienst', timeout: 'Zeitüberschreitung bei der Anfrage', httpError: 'Anfrage mit Status {status} fehlgeschlagen', downloading: 'Download läuft. Bitte warten...', downloadError: 'Der Download ist fehlgeschlagen. Wenden Sie sich an den Administrator.', duplicate: 'Die Anfrage wird verarbeitet. Bitte nicht erneut senden.' }
  },
  'fr-FR': {
    common: { confirm: 'Confirmer', cancel: 'Annuler', prompt: 'Information', systemPrompt: 'Information système' }, language: { label: 'Langue' }, account: { profile: 'Profil', layout: 'Paramètres de mise en page', logout: 'Se déconnecter', logoutConfirm: 'Voulez-vous vraiment vous déconnecter ?' },
    login: { account: 'Compte', password: 'Mot de passe', captcha: 'Code de vérification', remember: 'Mémoriser le mot de passe', signIn: 'Se connecter', signingIn: 'Connexion en cours...', register: "S'inscrire", loadingMode: 'Chargement du mode de connexion...', reload: 'Recharger', ssoEnabled: "L'authentification unique est activée", ssoTip: 'Ouvrez ce système depuis la plateforme unifiée.', unsupportedMode: 'Le serveur a renvoyé un mode de connexion non pris en charge', localInitFailed: "L'initialisation de la connexion locale a échoué. Réessayez plus tard.", modeLoadFailed: 'Impossible de charger le mode de connexion. Vérifiez le service backend.', accountRequired: 'Saisissez votre compte', passwordRequired: 'Saisissez votre mot de passe', captchaRequired: 'Saisissez le code de vérification', success: 'Connexion réussie' },
    tags: { refresh: 'Actualiser', close: 'Fermer', closeOthers: 'Fermer les autres', closeLeft: 'Fermer à gauche', closeRight: 'Fermer à droite', closeAll: 'Tout fermer' },
    request: { sessionExpired: 'Votre session a expiré. Vous pouvez rester sur cette page ou vous reconnecter.', relogin: 'Se reconnecter', invalidSession: 'La session est invalide ou a expiré. Reconnectez-vous.', network: 'Impossible de joindre le service backend', timeout: "La requête a dépassé le délai d'attente", httpError: 'La requête a échoué avec le statut {status}', downloading: 'Téléchargement en cours. Veuillez patienter...', downloadError: "Le téléchargement a échoué. Contactez l'administrateur.", duplicate: 'La requête est en cours de traitement. Ne la renvoyez pas.' }
  },
  'ja-JP': {
    common: { confirm: '確認', cancel: 'キャンセル', prompt: 'お知らせ', systemPrompt: 'システム通知' }, language: { label: '言語' }, account: { profile: 'プロフィール', layout: 'レイアウト設定', logout: 'ログアウト', logoutConfirm: 'ログアウトしてもよろしいですか？' },
    login: { account: 'アカウント', password: 'パスワード', captcha: '認証コード', remember: 'パスワードを保存', signIn: 'ログイン', signingIn: 'ログイン中...', register: '新規登録', loadingMode: 'ログイン方式を読み込んでいます...', reload: '再読み込み', ssoEnabled: 'シングルサインオンが有効です', ssoTip: '統合プラットフォームから本システムを開いてください。', unsupportedMode: 'サーバーから未対応のログイン方式が返されました', localInitFailed: 'ローカルログインの初期化に失敗しました。しばらくしてから再試行してください。', modeLoadFailed: 'ログイン方式を読み込めません。バックエンドサービスを確認してください。', accountRequired: 'アカウントを入力してください', passwordRequired: 'パスワードを入力してください', captchaRequired: '認証コードを入力してください', success: 'ログインしました' },
    tags: { refresh: '更新', close: '閉じる', closeOthers: '他を閉じる', closeLeft: '左側を閉じる', closeRight: '右側を閉じる', closeAll: 'すべて閉じる' },
    request: { sessionExpired: 'セッションの有効期限が切れました。このページに留まるか、再ログインしてください。', relogin: '再ログイン', invalidSession: 'セッションが無効または期限切れです。再ログインしてください。', network: 'バックエンドサービスに接続できません', timeout: 'リクエストがタイムアウトしました', httpError: 'リクエストに失敗しました（ステータス：{status}）', downloading: 'ダウンロード中です。お待ちください...', downloadError: 'ダウンロードに失敗しました。管理者にお問い合わせください。', duplicate: '処理中です。重複して送信しないでください。' }
  },
  'pt-BR': {
    common: { confirm: 'Confirmar', cancel: 'Cancelar', prompt: 'Aviso', systemPrompt: 'Aviso do sistema' }, language: { label: 'Idioma' }, account: { profile: 'Perfil', layout: 'Configurações de layout', logout: 'Sair', logoutConfirm: 'Deseja realmente sair?' },
    login: { account: 'Conta', password: 'Senha', captcha: 'Código de verificação', remember: 'Lembrar senha', signIn: 'Entrar', signingIn: 'Entrando...', register: 'Criar conta', loadingMode: 'Carregando método de acesso...', reload: 'Recarregar', ssoEnabled: 'O login único está ativado', ssoTip: 'Abra este sistema pela plataforma unificada.', unsupportedMode: 'O servidor retornou um método de acesso incompatível', localInitFailed: 'Não foi possível iniciar o login local. Tente novamente mais tarde.', modeLoadFailed: 'Não foi possível carregar o método de acesso. Verifique o serviço de backend.', accountRequired: 'Digite sua conta', passwordRequired: 'Digite sua senha', captchaRequired: 'Digite o código de verificação', success: 'Login realizado com sucesso' },
    tags: { refresh: 'Atualizar', close: 'Fechar', closeOthers: 'Fechar outras', closeLeft: 'Fechar à esquerda', closeRight: 'Fechar à direita', closeAll: 'Fechar todas' },
    request: { sessionExpired: 'Sua sessão expirou. Você pode permanecer nesta página ou entrar novamente.', relogin: 'Entrar novamente', invalidSession: 'A sessão é inválida ou expirou. Entre novamente.', network: 'Não foi possível conectar ao serviço de backend', timeout: 'A solicitação excedeu o tempo limite', httpError: 'A solicitação falhou com o status {status}', downloading: 'Baixando. Aguarde...', downloadError: 'Falha no download. Fale com o administrador.', duplicate: 'A solicitação está sendo processada. Não envie novamente.' }
  },
  'ru-RU': {
    common: { confirm: 'Подтвердить', cancel: 'Отмена', prompt: 'Уведомление', systemPrompt: 'Системное уведомление' }, language: { label: 'Язык' }, account: { profile: 'Профиль', layout: 'Настройки макета', logout: 'Выйти', logoutConfirm: 'Вы действительно хотите выйти?' },
    login: { account: 'Учётная запись', password: 'Пароль', captcha: 'Код подтверждения', remember: 'Запомнить пароль', signIn: 'Войти', signingIn: 'Выполняется вход...', register: 'Регистрация', loadingMode: 'Загрузка способа входа...', reload: 'Повторить', ssoEnabled: 'Единый вход включён', ssoTip: 'Откройте эту систему с единой платформы.', unsupportedMode: 'Сервер вернул неподдерживаемый способ входа', localInitFailed: 'Не удалось инициализировать локальный вход. Повторите попытку позже.', modeLoadFailed: 'Не удалось загрузить способ входа. Проверьте серверную службу.', accountRequired: 'Введите учётную запись', passwordRequired: 'Введите пароль', captchaRequired: 'Введите код подтверждения', success: 'Вход выполнен' },
    tags: { refresh: 'Обновить', close: 'Закрыть', closeOthers: 'Закрыть остальные', closeLeft: 'Закрыть слева', closeRight: 'Закрыть справа', closeAll: 'Закрыть все' },
    request: { sessionExpired: 'Срок действия сеанса истёк. Останьтесь на странице или войдите снова.', relogin: 'Войти снова', invalidSession: 'Сеанс недействителен или истёк. Войдите снова.', network: 'Не удалось подключиться к серверной службе', timeout: 'Время ожидания запроса истекло', httpError: 'Запрос завершился с кодом {status}', downloading: 'Идёт загрузка. Подождите...', downloadError: 'Не удалось загрузить файл. Обратитесь к администратору.', duplicate: 'Запрос обрабатывается. Не отправляйте его повторно.' }
  },
  'ko-KR': {
    common: { confirm: '확인', cancel: '취소', prompt: '알림', systemPrompt: '시스템 알림' }, language: { label: '언어' }, account: { profile: '내 프로필', layout: '레이아웃 설정', logout: '로그아웃', logoutConfirm: '로그아웃하시겠습니까?' },
    login: { account: '계정', password: '비밀번호', captcha: '인증 코드', remember: '비밀번호 기억', signIn: '로그인', signingIn: '로그인 중...', register: '회원가입', loadingMode: '로그인 방식을 불러오는 중...', reload: '다시 불러오기', ssoEnabled: '통합 로그인이 활성화되어 있습니다', ssoTip: '통합 플랫폼에서 이 시스템을 열어 주세요.', unsupportedMode: '서버에서 지원하지 않는 로그인 방식을 반환했습니다', localInitFailed: '로컬 로그인 초기화에 실패했습니다. 잠시 후 다시 시도해 주세요.', modeLoadFailed: '로그인 방식을 불러올 수 없습니다. 백엔드 서비스를 확인해 주세요.', accountRequired: '계정을 입력해 주세요', passwordRequired: '비밀번호를 입력해 주세요', captchaRequired: '인증 코드를 입력해 주세요', success: '로그인했습니다' },
    tags: { refresh: '새로고침', close: '닫기', closeOthers: '다른 탭 닫기', closeLeft: '왼쪽 탭 닫기', closeRight: '오른쪽 탭 닫기', closeAll: '모두 닫기' },
    request: { sessionExpired: '세션이 만료되었습니다. 이 페이지에 머물거나 다시 로그인해 주세요.', relogin: '다시 로그인', invalidSession: '세션이 유효하지 않거나 만료되었습니다. 다시 로그인해 주세요.', network: '백엔드 서비스에 연결할 수 없습니다', timeout: '요청 시간이 초과되었습니다', httpError: '요청 실패(상태 {status})', downloading: '다운로드 중입니다. 잠시 기다려 주세요...', downloadError: '다운로드에 실패했습니다. 관리자에게 문의해 주세요.', duplicate: '요청을 처리 중입니다. 다시 제출하지 마세요.' }
  },
  'id-ID': {
    common: { confirm: 'Konfirmasi', cancel: 'Batal', prompt: 'Pemberitahuan', systemPrompt: 'Pemberitahuan sistem' }, language: { label: 'Bahasa' }, account: { profile: 'Profil', layout: 'Pengaturan tata letak', logout: 'Keluar', logoutConfirm: 'Yakin ingin keluar?' },
    login: { account: 'Akun', password: 'Kata sandi', captcha: 'Kode verifikasi', remember: 'Ingat kata sandi', signIn: 'Masuk', signingIn: 'Sedang masuk...', register: 'Daftar sekarang', loadingMode: 'Memuat metode masuk...', reload: 'Muat ulang', ssoEnabled: 'Single sign-on aktif', ssoTip: 'Buka sistem ini dari platform terpadu.', unsupportedMode: 'Server mengembalikan metode masuk yang tidak didukung', localInitFailed: 'Inisialisasi login lokal gagal. Coba lagi nanti.', modeLoadFailed: 'Metode masuk tidak dapat dimuat. Periksa layanan backend.', accountRequired: 'Masukkan akun', passwordRequired: 'Masukkan kata sandi', captchaRequired: 'Masukkan kode verifikasi', success: 'Berhasil masuk' },
    tags: { refresh: 'Muat ulang', close: 'Tutup', closeOthers: 'Tutup lainnya', closeLeft: 'Tutup sebelah kiri', closeRight: 'Tutup sebelah kanan', closeAll: 'Tutup semua' },
    request: { sessionExpired: 'Sesi sudah berakhir. Anda dapat tetap di halaman ini atau masuk kembali.', relogin: 'Masuk kembali', invalidSession: 'Sesi tidak valid atau sudah berakhir. Silakan masuk kembali.', network: 'Tidak dapat terhubung ke layanan backend', timeout: 'Waktu permintaan habis', httpError: 'Permintaan gagal dengan status {status}', downloading: 'Sedang mengunduh. Tunggu...', downloadError: 'Unduhan gagal. Hubungi administrator.', duplicate: 'Permintaan sedang diproses. Jangan kirim ulang.' }
  },
  'tr-TR': {
    common: { confirm: 'Onayla', cancel: 'İptal', prompt: 'Bildirim', systemPrompt: 'Sistem bildirimi' }, language: { label: 'Dil' }, account: { profile: 'Profil', layout: 'Düzen ayarları', logout: 'Çıkış yap', logoutConfirm: 'Çıkış yapmak istediğinizden emin misiniz?' },
    login: { account: 'Hesap', password: 'Parola', captcha: 'Doğrulama kodu', remember: 'Parolayı hatırla', signIn: 'Giriş yap', signingIn: 'Giriş yapılıyor...', register: 'Şimdi kaydol', loadingMode: 'Giriş yöntemi yükleniyor...', reload: 'Yeniden yükle', ssoEnabled: 'Tek oturum açma etkin', ssoTip: 'Bu sistemi birleşik platformdan açın.', unsupportedMode: 'Sunucu desteklenmeyen bir giriş yöntemi döndürdü', localInitFailed: 'Yerel giriş başlatılamadı. Daha sonra tekrar deneyin.', modeLoadFailed: 'Giriş yöntemi yüklenemedi. Backend hizmetini kontrol edin.', accountRequired: 'Hesabınızı girin', passwordRequired: 'Parolanızı girin', captchaRequired: 'Doğrulama kodunu girin', success: 'Başarıyla giriş yapıldı' },
    tags: { refresh: 'Yenile', close: 'Kapat', closeOthers: 'Diğerlerini kapat', closeLeft: 'Soldakileri kapat', closeRight: 'Sağdakileri kapat', closeAll: 'Tümünü kapat' },
    request: { sessionExpired: 'Oturumunuz sona erdi. Bu sayfada kalabilir veya yeniden giriş yapabilirsiniz.', relogin: 'Yeniden giriş yap', invalidSession: 'Oturum geçersiz veya süresi dolmuş. Yeniden giriş yapın.', network: 'Backend hizmetine bağlanılamadı', timeout: 'İstek zaman aşımına uğradı', httpError: 'İstek {status} durumuyla başarısız oldu', downloading: 'İndiriliyor. Lütfen bekleyin...', downloadError: 'İndirme başarısız oldu. Yöneticiyle iletişime geçin.', duplicate: 'İstek işleniyor. Tekrar göndermeyin.' }
  }
}

// Values follow localeOrder. These are the route titles currently exposed by the WVP shell.
const menuRows = {
  '首页': ['首页', 'Home', 'Inicio', 'الرئيسية', 'Startseite', 'Accueil', 'ホーム', 'Início', 'Главная', '홈', 'Beranda', 'Ana sayfa'],
  '工作台': ['工作台', 'Workspace', 'Área de trabajo', 'مساحة العمل', 'Arbeitsbereich', 'Espace de travail', 'ワークスペース', 'Área de trabalho', 'Рабочая область', '작업 공간', 'Ruang kerja', 'Çalışma alanı'],
  '视频汇聚': ['视频汇聚', 'Video Hub', 'Centro de video', 'مركز الفيديو', 'Videozentrale', 'Centre vidéo', '映像ハブ', 'Central de vídeo', 'Видеоцентр', '비디오 허브', 'Pusat video', 'Video merkezi'],
  '系统设置': ['系统设置', 'System Settings', 'Configuración del sistema', 'إعدادات النظام', 'Systemeinstellungen', 'Paramètres système', 'システム設定', 'Configurações do sistema', 'Системные настройки', '시스템 설정', 'Pengaturan sistem', 'Sistem ayarları'],
  '电子地图': ['电子地图', 'Map', 'Mapa', 'الخريطة', 'Karte', 'Carte', 'マップ', 'Mapa', 'Карта', '지도', 'Peta', 'Harita'],
  '设备管理': ['设备管理', 'Device Management', 'Administración de dispositivos', 'إدارة الأجهزة', 'Geräteverwaltung', 'Gestion des appareils', 'デバイス管理', 'Gerenciamento de dispositivos', 'Управление устройствами', '장치 관리', 'Manajemen perangkat', 'Cihaz yönetimi'],
  '设备列表': ['设备列表', 'Device List', 'Lista de dispositivos', 'قائمة الأجهزة', 'Geräteliste', 'Liste des appareils', 'デバイス一覧', 'Lista de dispositivos', 'Список устройств', '장치 목록', 'Daftar perangkat', 'Cihaz listesi'],
  '国标协议': ['国标协议', 'GB28181', 'GB28181', 'GB28181', 'GB28181', 'GB28181', 'GB28181', 'GB28181', 'GB28181', 'GB28181', 'GB28181', 'GB28181'],
  '国际协议': ['国际协议', 'GB28181', 'GB28181', 'GB28181', 'GB28181', 'GB28181', 'GB28181', 'GB28181', 'GB28181', 'GB28181', 'GB28181', 'GB28181'],
  'ONVIF协议': ['ONVIF协议', 'ONVIF', 'ONVIF', 'ONVIF', 'ONVIF', 'ONVIF', 'ONVIF', 'ONVIF', 'ONVIF', 'ONVIF', 'ONVIF', 'ONVIF'],
  'onvif协议': ['onvif协议', 'ONVIF', 'ONVIF', 'ONVIF', 'ONVIF', 'ONVIF', 'ONVIF', 'ONVIF', 'ONVIF', 'ONVIF', 'ONVIF', 'ONVIF'],
  'RTSP协议': ['RTSP协议', 'RTSP', 'RTSP', 'RTSP', 'RTSP', 'RTSP', 'RTSP', 'RTSP', 'RTSP', 'RTSP', 'RTSP', 'RTSP'],
  'rtsp协议': ['rtsp协议', 'RTSP', 'RTSP', 'RTSP', 'RTSP', 'RTSP', 'RTSP', 'RTSP', 'RTSP', 'RTSP', 'RTSP', 'RTSP'],
  'ISUP协议': ['ISUP协议', 'ISUP', 'ISUP', 'ISUP', 'ISUP', 'ISUP', 'ISUP', 'ISUP', 'ISUP', 'ISUP', 'ISUP', 'ISUP'],
  'isup协议': ['isup协议', 'ISUP', 'ISUP', 'ISUP', 'ISUP', 'ISUP', 'ISUP', 'ISUP', 'ISUP', 'ISUP', 'ISUP', 'ISUP'],
  '海康协议': ['海康协议', 'Hikvision Protocol', 'Protocolo Hikvision', 'بروتوكول Hikvision', 'Hikvision-Protokoll', 'Protocole Hikvision', 'Hikvisionプロトコル', 'Protocolo Hikvision', 'Протокол Hikvision', 'Hikvision 프로토콜', 'Protokol Hikvision', 'Hikvision protokolü'],
  'EHome协议': ['EHome协议', 'EHome', 'EHome', 'EHome', 'EHome', 'EHome', 'EHome', 'EHome', 'EHome', 'EHome', 'EHome', 'EHome'],
  '大华协议': ['大华协议', 'Dahua Protocol', 'Protocolo Dahua', 'بروتوكول Dahua', 'Dahua-Protokoll', 'Protocole Dahua', 'Dahuaプロトコル', 'Protocolo Dahua', 'Протокол Dahua', 'Dahua 프로토콜', 'Protokol Dahua', 'Dahua protokolü'],
  '萤石协议': ['萤石协议', 'EZVIZ Protocol', 'Protocolo EZVIZ', 'بروتوكول EZVIZ', 'EZVIZ-Protokoll', 'Protocole EZVIZ', 'EZVIZプロトコル', 'Protocolo EZVIZ', 'Протокол EZVIZ', 'EZVIZ 프로토콜', 'Protokol EZVIZ', 'EZVIZ protokolü'],
  '乐橙协议': ['乐橙协议', 'Imou Protocol', 'Protocolo Imou', 'بروتوكول Imou', 'Imou-Protokoll', 'Protocole Imou', 'Imouプロトコル', 'Protocolo Imou', 'Протокол Imou', 'Imou 프로토콜', 'Protokol Imou', 'Imou protokolü'],
  '自定义协议': ['自定义协议', 'Custom Protocol', 'Protocolo personalizado', 'بروتوكول مخصص', 'Benutzerdefiniertes Protokoll', 'Protocole personnalisé', 'カスタムプロトコル', 'Protocolo personalizado', 'Пользовательский протокол', '사용자 정의 프로토콜', 'Protokol khusus', 'Özel protokol'],
  '系统管理': ['系统管理', 'System Management', 'Administración del sistema', 'إدارة النظام', 'Systemverwaltung', 'Administration système', 'システム管理', 'Gerenciamento do sistema', 'Управление системой', '시스템 관리', 'Manajemen sistem', 'Sistem yönetimi'],
  '系统监控': ['系统监控', 'System Monitoring', 'Monitoreo del sistema', 'مراقبة النظام', 'Systemüberwachung', 'Surveillance système', 'システム監視', 'Monitoramento do sistema', 'Мониторинг системы', '시스템 모니터링', 'Pemantauan sistem', 'Sistem izleme'],
  '系统工具': ['系统工具', 'System Tools', 'Herramientas del sistema', 'أدوات النظام', 'Systemwerkzeuge', 'Outils système', 'システムツール', 'Ferramentas do sistema', 'Системные инструменты', '시스템 도구', 'Alat sistem', 'Sistem araçları'],
  '用户管理': ['用户管理', 'Users', 'Usuarios', 'المستخدمون', 'Benutzer', 'Utilisateurs', 'ユーザー', 'Usuários', 'Пользователи', '사용자', 'Pengguna', 'Kullanıcılar'],
  '角色管理': ['角色管理', 'Roles', 'Roles', 'الأدوار', 'Rollen', 'Rôles', 'ロール', 'Funções', 'Роли', '역할', 'Peran', 'Roller'],
  '菜单管理': ['菜单管理', 'Menus', 'Menús', 'القوائم', 'Menüs', 'Menus', 'メニュー', 'Menus', 'Меню', '메뉴', 'Menu', 'Menüler'],
  '部门管理': ['部门管理', 'Departments', 'Departamentos', 'الأقسام', 'Abteilungen', 'Départements', '部門', 'Departamentos', 'Отделы', '부서', 'Departemen', 'Departmanlar'],
  '岗位管理': ['岗位管理', 'Positions', 'Puestos', 'المناصب', 'Positionen', 'Postes', '職位', 'Cargos', 'Должности', '직위', 'Jabatan', 'Pozisyonlar'],
  '字典管理': ['字典管理', 'Dictionaries', 'Catálogos', 'القواميس', 'Wörterbücher', 'Dictionnaires', '辞書', 'Dicionários', 'Справочники', '사전', 'Kamus', 'Sözlükler'],
  '参数设置': ['参数设置', 'Parameters', 'Parámetros', 'المعلمات', 'Parameter', 'Paramètres', 'パラメーター', 'Parâmetros', 'Параметры', '매개변수', 'Parameter', 'Parametreler'],
  '通知公告': ['通知公告', 'Notices', 'Avisos', 'الإشعارات', 'Mitteilungen', 'Avis', 'お知らせ', 'Avisos', 'Уведомления', '공지', 'Pemberitahuan', 'Duyurular'],
  '在线用户': ['在线用户', 'Online Users', 'Usuarios en línea', 'المستخدمون المتصلون', 'Online-Benutzer', 'Utilisateurs en ligne', 'オンラインユーザー', 'Usuários online', 'Пользователи онлайн', '온라인 사용자', 'Pengguna online', 'Çevrimiçi kullanıcılar'],
  '操作日志': ['操作日志', 'Operation Logs', 'Registros de operación', 'سجلات العمليات', 'Aktionsprotokolle', "Journaux d'opérations", '操作ログ', 'Logs de operação', 'Журнал операций', '작업 로그', 'Log operasi', 'İşlem günlükleri'],
  '登录日志': ['登录日志', 'Sign-in Logs', 'Registros de acceso', 'سجلات الدخول', 'Anmeldeprotokolle', 'Journaux de connexion', 'ログイン履歴', 'Logs de acesso', 'Журнал входов', '로그인 로그', 'Log masuk', 'Giriş günlükleri'],
  '服务监控': ['服务监控', 'Service Monitoring', 'Monitoreo de servicios', 'مراقبة الخدمات', 'Dienstüberwachung', 'Surveillance des services', 'サービス監視', 'Monitoramento de serviços', 'Мониторинг служб', '서비스 모니터링', 'Pemantauan layanan', 'Hizmet izleme'],
  '缓存监控': ['缓存监控', 'Cache Monitoring', 'Monitoreo de caché', 'مراقبة التخزين المؤقت', 'Cache-Überwachung', 'Surveillance du cache', 'キャッシュ監視', 'Monitoramento de cache', 'Мониторинг кэша', '캐시 모니터링', 'Pemantauan cache', 'Önbellek izleme'],
  '定时任务': ['定时任务', 'Scheduled Jobs', 'Tareas programadas', 'المهام المجدولة', 'Geplante Aufgaben', 'Tâches planifiées', 'スケジュールタスク', 'Tarefas agendadas', 'Планировщик заданий', '예약 작업', 'Tugas terjadwal', 'Zamanlanmış görevler'],
  '表单构建': ['表单构建', 'Form Builder', 'Generador de formularios', 'منشئ النماذج', 'Formular-Designer', 'Créateur de formulaires', 'フォームビルダー', 'Criador de formulários', 'Конструктор форм', '양식 빌더', 'Pembuat formulir', 'Form oluşturucu'],
  '代码生成': ['代码生成', 'Code Generation', 'Generación de código', 'إنشاء التعليمات البرمجية', 'Codegenerierung', 'Génération de code', 'コード生成', 'Geração de código', 'Генерация кода', '코드 생성', 'Pembuatan kode', 'Kod oluşturma'],
  '系统接口': ['系统接口', 'API Documentation', 'Documentación de API', 'وثائق API', 'API-Dokumentation', 'Documentation API', 'APIドキュメント', 'Documentação da API', 'Документация API', 'API 문서', 'Dokumentasi API', 'API belgeleri'],
  '国标设备': ['国标设备', 'GB28181 Devices', 'Dispositivos GB28181', 'أجهزة GB28181', 'GB28181-Geräte', 'Appareils GB28181', 'GB28181デバイス', 'Dispositivos GB28181', 'Устройства GB28181', 'GB28181 장치', 'Perangkat GB28181', 'GB28181 cihazları'],
  '国标级联': ['国标级联', 'GB28181 Cascading', 'Cascada GB28181', 'ربط GB28181', 'GB28181-Kaskadierung', 'Cascade GB28181', 'GB28181カスケード', 'Cascata GB28181', 'Каскадирование GB28181', 'GB28181 캐스케이드', 'Kaskade GB28181', 'GB28181 kademelendirme'],
  '分屏监控': ['分屏监控', 'Multi-view Monitoring', 'Monitoreo multipantalla', 'مراقبة متعددة الشاشات', 'Mehrfachansicht', 'Surveillance multi-écrans', 'マルチビュー監視', 'Monitoramento multivisão', 'Мультиэкранный мониторинг', '다중 화면 모니터링', 'Pemantauan multilayar', 'Çoklu ekran izleme'],
  '录像计划': ['录像计划', 'Recording Schedule', 'Programa de grabación', 'جدول التسجيل', 'Aufnahmeplan', "Programme d'enregistrement", '録画スケジュール', 'Agenda de gravação', 'Расписание записи', '녹화 일정', 'Jadwal perekaman', 'Kayıt programı'],
  '设备录像': ['设备录像', 'Device Recordings', 'Grabaciones del dispositivo', 'تسجيلات الجهاز', 'Geräteaufnahmen', "Enregistrements de l'appareil", 'デバイス録画', 'Gravações do dispositivo', 'Записи устройства', '장치 녹화', 'Rekaman perangkat', 'Cihaz kayıtları'],
  '拉流列表': ['拉流列表', 'Incoming Streams', 'Flujos entrantes', 'التدفقات الواردة', 'Eingehende Streams', 'Flux entrants', '受信ストリーム', 'Fluxos de entrada', 'Входящие потоки', '수신 스트림', 'Stream masuk', 'Gelen akışlar'],
  '推流列表': ['推流列表', 'Outgoing Streams', 'Flujos salientes', 'التدفقات الصادرة', 'Ausgehende Streams', 'Flux sortants', '送信ストリーム', 'Fluxos de saída', 'Исходящие потоки', '송신 스트림', 'Stream keluar', 'Giden akışlar'],
  '通道管理': ['通道管理', 'Channel Management', 'Administración de canales', 'إدارة القنوات', 'Kanalverwaltung', 'Gestion des canaux', 'チャンネル管理', 'Gerenciamento de canais', 'Управление каналами', '채널 관리', 'Manajemen kanal', 'Kanal yönetimi'],
  '通道列表': ['通道列表', 'Channel List', 'Lista de canales', 'قائمة القنوات', 'Kanalliste', 'Liste des canaux', 'チャンネル一覧', 'Lista de canais', 'Список каналов', '채널 목록', 'Daftar kanal', 'Kanal listesi'],
  '视图库': ['视图库', 'Video and Image Library', 'Biblioteca audiovisual', 'مكتبة الفيديو والصور', 'Video- und Bildbibliothek', "Bibliothèque d'images et de vidéos", '映像・画像ライブラリ', 'Biblioteca de vídeos e imagens', 'Библиотека видео и изображений', '영상 및 이미지 라이브러리', 'Pustaka video dan gambar', 'Video ve görsel kitaplığı'],
  '节点管理': ['节点管理', 'Node Management', 'Administración de nodos', 'إدارة العُقد', 'Knotenverwaltung', 'Gestion des nœuds', 'ノード管理', 'Gerenciamento de nós', 'Управление узлами', '노드 관리', 'Manajemen node', 'Düğüm yönetimi'],
  'VLStream协议': ['VLStream协议', 'VLStream', 'VLStream', 'VLStream', 'VLStream', 'VLStream', 'VLStream', 'VLStream', 'VLStream', 'VLStream', 'VLStream', 'VLStream'],
  '行政分组': ['行政分组', 'Administrative Groups', 'Grupos administrativos', 'المجموعات الإدارية', 'Verwaltungsgruppen', 'Groupes administratifs', '行政グループ', 'Grupos administrativos', 'Административные группы', '행정 그룹', 'Grup administratif', 'Yönetim grupları'],
  '报警管理': ['报警管理', 'Alarm Management', 'Administración de alarmas', 'إدارة الإنذارات', 'Alarmverwaltung', 'Gestion des alarmes', 'アラーム管理', 'Gerenciamento de alarmes', 'Управление тревогами', '알람 관리', 'Manajemen alarm', 'Alarm yönetimi'],
  '日志管理': ['日志管理', 'Log Management', 'Administración de registros', 'إدارة السجلات', 'Protokollverwaltung', 'Gestion des journaux', 'ログ管理', 'Gerenciamento de logs', 'Управление журналами', '로그 관리', 'Manajemen log', 'Günlük yönetimi']
}

const loginRows = {
  productTitle: ['视频监控平台', 'Video Surveillance Platform', 'Plataforma de videovigilancia', 'منصة المراقبة بالفيديو', 'Videoüberwachungsplattform', 'Plateforme de vidéosurveillance', '映像監視プラットフォーム', 'Plataforma de videomonitoramento', 'Платформа видеонаблюдения', '영상 모니터링 플랫폼', 'Platform pengawasan video', 'Video gözetim platformu'],
  copyright: ['视频监控平台。保留所有权利。', 'Video Surveillance Platform. All rights reserved.', 'Plataforma de videovigilancia. Todos los derechos reservados.', 'منصة المراقبة بالفيديو. جميع الحقوق محفوظة.', 'Videoüberwachungsplattform. Alle Rechte vorbehalten.', 'Plateforme de vidéosurveillance. Tous droits réservés.', '映像監視プラットフォーム。無断転載を禁じます。', 'Plataforma de videomonitoramento. Todos os direitos reservados.', 'Платформа видеонаблюдения. Все права защищены.', '영상 모니터링 플랫폼. 모든 권리 보유.', 'Platform pengawasan video. Hak cipta dilindungi.', 'Video gözetim platformu. Tüm hakları saklıdır.']
}

for (const [key, values] of Object.entries(loginRows)) {
  localeOrder.forEach((locale, index) => {
    namedMessages[locale].login[key] = values[index]
  })
}

for (const [title, values] of Object.entries(menuRows)) {
  localeOrder.forEach((locale, index) => {
    namedMessages[locale].menu ||= {}
    namedMessages[locale].menu[title] = values[index]
  })
}

export default namedMessages
