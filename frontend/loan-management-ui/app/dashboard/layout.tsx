'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';

import Sidebar from '@/components/Sidebar';
import { AuthContext, useAuthState } from '@/hooks/useAuth';
import { ToastContainer } from '@/components/ui/ToastContainer';
import { OfflineProvider } from '@/components/OfflineProvider';
import ForcedPasswordChange from '@/components/ForcedPasswordChange';

const authHeader = (): Record<string, string> => {
if (typeof window === 'undefined') {
return {};
}

const token = localStorage.getItem('token');

if (!token) {
return {};
}

return {
Authorization: `Bearer ${token}`,
};
};

export default function DashboardLayout({
children,
}: {
children: React.ReactNode;
}) {
const auth = useAuthState();
const router = useRouter();

/*

* ============================================================
* AUTHENTICATION CHECK
* ============================================================
  */
  useEffect(() => {
  if (!auth.loading && !auth.user) {
  router.replace('/login');
  }
  }, [auth.loading, auth.user, router]);

/*

* ============================================================
* LOADING SCREEN
* ============================================================
  */
  if (auth.loading) {
  return (

   <div className="min-h-screen bg-[#F7FAF8] flex items-center justify-center">
     <div className="flex flex-col items-center gap-4">
       <div className="relative h-12 w-12">
         <div className="absolute inset-0 rounded-full border-4 border-emerald-100" />

  
     <div className="absolute inset-0 animate-spin rounded-full border-4 border-transparent border-t-emerald-600 border-r-emerald-500" />
   </div>

   <div className="text-center">
     <p className="text-sm font-bold text-gray-900">
       Loading Growth Finance
     </p>

     <p className="mt-1 text-xs text-gray-500">
       Preparing your financial workspace…
     </p>
   </div>
  

     </div>
   </div>
);

}

/*

* ============================================================
* USER NOT AUTHENTICATED
* ============================================================
  */
  if (!auth.user) {
  return null;
  }

/*

* ============================================================
* FORCE PASSWORD CHANGE
* ============================================================
  */
  if (auth.mustChangePassword) {
  return (
  <AuthContext.Provider value={auth}>

     <div className="min-h-screen bg-[#F7FAF8]">
       <ForcedPasswordChange />
     </div>

     <ToastContainer />
   </AuthContext.Provider>


);


}

/*

* ============================================================
* MAIN DASHBOARD
* ============================================================
  */
  return (
  <AuthContext.Provider value={auth}> <OfflineProvider authHeader={authHeader} />

   <div className="min-h-screen bg-[#F7FAF8] text-gray-900">
     <div className="flex min-h-screen">


   {/*
    * ======================================================
    * SIDEBAR
    * ======================================================
    *
    * Your existing Sidebar remains responsible for:
    *
    * Dashboard
    * Loans
    * Borrowers
    * Payments
    * Reports
    * Settings
    * etc.
    *
    * We do NOT modify it here.
    *
    * ======================================================
    */}
   <aside className="fixed left-0 top-0 bottom-0 z-40 w-64">
     <Sidebar />
   </aside>

   {/*
    * ======================================================
    * RIGHT APPLICATION AREA
    * ======================================================
    */}
   <div className="flex min-h-screen flex-1 flex-col pl-64">

     {/*
      * ====================================================
      * TOP NAVIGATION BAR
      * ====================================================
      */}
     <header className="sticky top-0 z-30 h-[72px] border-b border-gray-200/80 bg-white/95 backdrop-blur-xl">
       <div className="flex h-full items-center justify-between px-7">

         {/*
          * ------------------------------------------------
          * LEFT SIDE
          * ------------------------------------------------
          */}
         <div className="flex items-center gap-4">

           <div>
             <p className="text-[11px] font-semibold uppercase tracking-[0.18em] text-emerald-600">
               Growth Finance
             </p>

             <h2 className="text-sm font-bold text-gray-900">
               Loan Management Platform
             </h2>
           </div>

         </div>

         {/*
          * ------------------------------------------------
          * RIGHT SIDE
          * ------------------------------------------------
          */}
         <div className="flex items-center gap-3">

           {/*
            * SYSTEM STATUS
            */}
           <div className="hidden items-center gap-2 rounded-full border border-emerald-100 bg-emerald-50 px-3 py-1.5 sm:flex">

             <span className="relative flex h-2 w-2">
               <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-emerald-400 opacity-75" />

               <span className="relative inline-flex h-2 w-2 rounded-full bg-emerald-500" />
             </span>

             <span className="text-xs font-semibold text-emerald-700">
               System Online
             </span>

           </div>

           {/*
            * NOTIFICATIONS
            */}
           <button
             type="button"
             aria-label="Notifications"
             className="
               relative
               flex
               h-10
               w-10
               items-center
               justify-center
               rounded-xl
               border
               border-gray-200
               bg-white
               text-gray-500
               transition
               hover:border-emerald-200
               hover:bg-emerald-50
               hover:text-emerald-700
             "
           >
             <span className="text-lg">
               🔔
             </span>

             <span className="absolute right-2 top-2 h-2 w-2 rounded-full border-2 border-white bg-yellow-400" />
           </button>

           {/*
            * ORGANIZATION PROFILE
            *
            * IMPORTANT:
            * We only use organizationName here because
            * that property is already confirmed to exist
            * in your AuthResponse.
            */}
           <div className="flex items-center gap-3 border-l border-gray-200 pl-4">

             <div className="hidden text-right sm:block">

               <p className="text-sm font-bold text-gray-900">
                 {auth.user.organizationName || 'Growth Finance'}
               </p>

               <p className="text-[11px] font-medium text-gray-500">
                 Financial Management Workspace
               </p>

             </div>

             {/*
              * Growth Finance avatar
              *
              * We intentionally use "GF" rather than trying
              * to access firstName, username, name, etc.
              */}
             <div className="
               relative
               flex
               h-10
               w-10
               items-center
               justify-center
               rounded-xl
               bg-gradient-to-br
               from-emerald-600
               to-green-700
               text-xs
               font-extrabold
               text-white
               shadow-sm
             ">
               GF

               <span className="absolute -bottom-0.5 -right-0.5 h-3 w-3 rounded-full border-2 border-white bg-yellow-400" />
             </div>

           </div>

         </div>
       </div>
     </header>

     {/*
      * ====================================================
      * MAIN CONTENT
      * ====================================================
      */}
     <main className="flex-1">

       <div className="
         mx-auto
         w-full
         max-w-[1800px]
         px-5
         py-6
         sm:px-7
         lg:px-8
         lg:py-7
       ">
         {children}
       </div>

     </main>

     {/*
      * ====================================================
      * FOOTER
      * ====================================================
      */}
     <footer className="border-t border-gray-200/70 bg-white/70 px-7 py-4">

       <div className="
         flex
         flex-col
         items-center
         justify-between
         gap-2
         text-[11px]
         text-gray-400
         sm:flex-row
       ">

         <p>
           © {new Date().getFullYear()} Growth Finance. All rights reserved.
         </p>

         <div className="flex items-center gap-2">

           <span>
             Secure Financial Platform
           </span>

           <span className="h-1 w-1 rounded-full bg-yellow-400" />

           <span>
             Loan Management System
           </span>

         </div>

       </div>

     </footer>

   </div>
  

     </div>
   </div>

   <ToastContainer />


</AuthContext.Provider>


);
}

