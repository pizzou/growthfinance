'use client';

import {
useState,
useEffect,
createContext,
useContext,
} from 'react';

import { AuthResponse } from '@/types';

interface AuthCtx {
user: AuthResponse | null;
token: string | null;

login: (user: AuthResponse, token: string) => void;
logout: () => void;

loading: boolean;

isAdmin: boolean;
isOfficer: boolean;

currency: string;
locale: string;

mustChangePassword: boolean;
}

export const AuthContext = createContext<AuthCtx>({
user: null,
token: null,

login: () => {},
logout: () => {},

loading: true,

isAdmin: false,
isOfficer: false,

currency: 'USD',
locale: 'en-US',

mustChangePassword: false,
});

export function useAuth() {
return useContext(AuthContext);
}

export function useAuthState() {
const [user, setUser] = useState<AuthResponse | null>(null);
const [token, setToken] = useState<string | null>(null);
const [loading, setLoading] = useState(true);

/*

* ============================================================
* RESTORE AUTHENTICATED SESSION
* ============================================================
  */
  useEffect(() => {
  try {
  const storedUser = localStorage.getItem('user');
  const storedToken = localStorage.getItem('token');

  if (storedUser && storedToken) {
  const parsedUser: AuthResponse = JSON.parse(storedUser);

  /*
  * Basic validation.
  *
  * This prevents malformed localStorage data from being
  * treated as a valid authenticated session.
  */
  if (
  parsedUser &&
  typeof parsedUser === 'object' &&
  typeof parsedUser.name === 'string' &&
  typeof parsedUser.email === 'string' &&
  typeof parsedUser.organizationName === 'string'
  ) {
  setUser(parsedUser);
  setToken(storedToken);
  } else {
  localStorage.removeItem('user');
  localStorage.removeItem('token');
  }
  }
  } catch (error) {
  console.error(
  'Failed to restore authentication session:',
  error
  );

  localStorage.removeItem('user');
  localStorage.removeItem('token');

  setUser(null);
  setToken(null);
  } finally {
  setLoading(false);
  }
  }, []);

/*

* ============================================================
* LOGIN
* ============================================================
  */
  const login = (
  userData: AuthResponse,
  tok: string
  ) => {
  /*

  * JWT validation.
    */
    if (
    !tok ||
    typeof tok !== 'string' ||
    tok.split('.').length !== 3
    ) {
    console.error(
    'useAuth.login() called with an invalid token:',
    tok
    );

  return;
  }


/*



 * Validate the important fields returned by the backend.
 */
if (
  !userData ||
  typeof userData !== 'object' ||
  typeof userData.userId !== 'number' ||
  typeof userData.name !== 'string' ||
  typeof userData.email !== 'string' ||
  typeof userData.organizationId !== 'number' ||
  typeof userData.organizationName !== 'string'
) {
  console.error(
    'useAuth.login() received an invalid AuthResponse:',
    userData
  );

  return;
}

/*
 * Persist session.
 */
localStorage.setItem('token', tok);
localStorage.setItem(
  'user',
  JSON.stringify(userData)
);

/*
 * Update React state.
 */
setUser(userData);
setToken(tok);


};

/*

* ============================================================
* LOGOUT
* ============================================================
  */
  const logout = () => {
  localStorage.removeItem('token');
  localStorage.removeItem('user');

setUser(null);



setToken(null);


};

/*

* ============================================================
* ROLE HELPERS
* ============================================================
  */
  const isAdmin =
  user?.role === 'ADMIN';

const isOfficer =
[
'ADMIN',
'LOAN_OFFICER',
'CREDIT_ANALYST',
'MANAGER',
].includes(user?.role || '');

/*

* ============================================================
* ORGANIZATION SETTINGS
* ============================================================
  */
  const currency =
  user?.currency || 'USD';

const locale =
user?.locale || 'en-US';

/*

* ============================================================
* PASSWORD CHANGE
* ============================================================
  */
  const mustChangePassword =
  Boolean(user?.mustChangePassword);

/*

* ============================================================
* RETURN AUTH STATE
* ============================================================
  */
  return {
  user,
  token,


loading,



login,
logout,

isAdmin,
isOfficer,

currency,
locale,

mustChangePassword,


};
}
