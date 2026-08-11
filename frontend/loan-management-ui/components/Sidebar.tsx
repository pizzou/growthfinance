'use client';

import Image from 'next/image';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useEffect, useState } from 'react';

import { useAuth } from '@/hooks/useAuth';
import { getUnreadCount } from '@/services/notificationsService';
import { contactMessageApi } from '@/services/api';


// ============================================================
// NAVIGATION TYPES
// ============================================================

type NavItem = {
  href: string;
  icon: string;
  label: string;
  adminOnly?: boolean;
  accountingOnly?: boolean;
};

type NavSection = {
  section: string;
  items: NavItem[];
};


// ============================================================
// MAIN NAVIGATION
// ============================================================

const NAV_STAFF: NavSection[] = [

  // ==========================================================
  // OVERVIEW
  // ==========================================================

  {
    section: 'Overview',

    items: [

      {
        href: '/dashboard',
        icon: '📊',
        label: 'Dashboard',
      },

      {
        href: '/dashboard/loans',
        icon: '💼',
        label: 'Loan Portfolio',
      },

      {
        href: '/dashboard/borrowers',
        icon: '👥',
        label: 'Borrowers',
      },

      {
        href: '/dashboard/payments',
        icon: '💳',
        label: 'Payments',
      },

      {
        href: '/dashboard/collections',
        icon: '📉',
        label: 'Collections',
      },

      {
        href: '/dashboard/notifications',
        icon: '🔔',
        label: 'Notifications',
      },

      {
        href: '/dashboard/messages',
        icon: '📬',
        label: 'Messages',
      },

    ],
  },


  // ==========================================================
  // TOOLS
  // ==========================================================

  {
    section: 'Tools',

    items: [

      {
        href: '/dashboard/reports',
        icon: '📈',
        label: 'Reports',
      },

      {
        href: '/dashboard/documents',
        icon: '🗂️',
        label: 'Internal Documents',
      },

      {
        href: '/dashboard/currencies',
        icon: '💱',
        label: 'FX Rates',
      },

      {
        href: '/dashboard/webhooks',
        icon: '🔗',
        label: 'Webhooks',
      },

    ],
  },


  // ==========================================================
  // ADMIN
  // ==========================================================

  {
    section: 'Admin',

    items: [

      {
        href: '/dashboard/products',
        icon: '💰',
        label: 'Loan Products',
        adminOnly: true,
      },

      {
        href: '/dashboard/import',
        icon: '📥',
        label: 'Import Legacy Loans',
      },

      {
        href: '/dashboard/accounting',
        icon: '📒',
        label: 'Accounting',
        accountingOnly: true,
      },

      {
        href: '/dashboard/expenses',
        icon: '🧾',
        label: 'Expenses',
        accountingOnly: true,
      },

      {
        href: '/dashboard/users',
        icon: '🧑‍💼',
        label: 'Users & Roles',
        adminOnly: true,
      },

      {
        href: '/dashboard/audit',
        icon: '🛡️',
        label: 'Audit Log',
        adminOnly: true,
      },

      {
        href: '/dashboard/settings',
        icon: '⚙️',
        label: 'Settings',
      },

    ],
  },

];


// ============================================================
// REGULATORY NAVIGATION
// ============================================================

const REGULATORY_ITEMS: NavItem[] = [

  {
    href: '/dashboard/reports/regulatory/bnr',
    icon: '🏦',
    label: 'BNR Reports',
  },

  {
    href: '/dashboard/reports/regulatory/crb',
    icon: '🧾',
    label: 'Credit Bureau',
  },

  {
    href: '/dashboard/reports/regulatory/api-keys',
    icon: '🔑',
    label: 'API Keys',
  },

];


// ============================================================
// SIDEBAR
// ============================================================

export default function Sidebar() {

  const pathname =
    usePathname();

  const {
    user,
    logout,
    currency,
  } = useAuth();


  // ==========================================================
  // ORGANIZATION
  // ==========================================================

  const org =
    user
      ? {
          name:
            user.organizationName,
          currency,
        }
      : null;


  // ==========================================================
  // PERMISSIONS
  // ==========================================================

  const isAdmin =
    user?.role === 'ADMIN';


  const canSeeAccounting =
    [
      'ADMIN',
      'MANAGER',
      'ACCOUNTANT',
    ].includes(
      user?.role || ''
    );


  // ==========================================================
  // NOTIFICATIONS
  // ==========================================================

  const [
    unread,
    setUnread,
  ] = useState(0);


  // ==========================================================
  // MESSAGES
  // ==========================================================

  const [
    unreadMessages,
    setUnreadMessages,
  ] = useState(0);


  // ==========================================================
  // REGULATORY OPEN STATE
  // ==========================================================

  const isRegulatoryRoute =
    pathname.startsWith(
      '/dashboard/reports/regulatory'
    );


  const [
    regulatoryOpen,
    setRegulatoryOpen,
  ] = useState(
    isRegulatoryRoute
  );


  // ==========================================================
  // KEEP REGULATORY OPEN WHEN NAVIGATING INSIDE IT
  // ==========================================================

  useEffect(() => {

    if (isRegulatoryRoute) {

      setRegulatoryOpen(true);

    }

  }, [
    isRegulatoryRoute,
  ]);


  // ==========================================================
  // LOAD NOTIFICATIONS
  // ==========================================================

  useEffect(() => {

    if (!user) {
      return;
    }


    const load = () => {

      getUnreadCount()
        .then(
          response =>
            setUnread(
              response.count
            )
        )
        .catch(
          () => {}
        );

    };


    load();


    const interval =
      setInterval(
        load,
        30000
      );


    return () =>
      clearInterval(
        interval
      );

  }, [
    user,
  ]);


  // ==========================================================
  // LOAD MESSAGES
  // ==========================================================

  useEffect(() => {

    if (!user) {
      return;
    }


    const load = () => {

      contactMessageApi
        .unreadCount()
        .then(
          (response: any) =>
            setUnreadMessages(
              response.count
            )
        )
        .catch(
          () => {}
        );

    };


    load();


    const interval =
      setInterval(
        load,
        30000
      );


    return () =>
      clearInterval(
        interval
      );

  }, [
    user,
  ]);


  // ==========================================================
  // ACTIVE ROUTE
  // ==========================================================

  const isActive = (
    href: string
  ): boolean => {

    return (
      pathname === href ||
      (
        href !== '/dashboard' &&
        pathname.startsWith(
          href
        )
      )
    );

  };


  // ==========================================================
  // LOGOUT
  // ==========================================================

  const handleLogout = () => {

    logout();

    window.location.href =
      '/login';

  };


  // ==========================================================
  // RENDER
  // ==========================================================

  return (

    <aside
      className="
        fixed
        left-0
        top-0
        bottom-0
        z-40
        flex
        min-h-screen
        w-64
        flex-col
        bg-[#0D1B2A]
      "
    >


      {/* ================================================== */}
      {/* BRAND */}
      {/* ================================================== */}

      <div
        className="
          flex
          items-center
          gap-3
          border-b
          border-white/10
          px-5
          py-4
        "
      >

        

        <div
          className="
            flex
            h-9
            w-9
            items-center
            justify-center
            rounded-lg
            text-base
            font-bold
            text-white
          "
          style={{
            backgroundColor:
              '#FFFFFF',
          }}
        >
          <Image
            src="/logo-mark.png"
            alt="Growth Finance Services"
            width={40}
            height={40}
            priority
            className="
              h-full
              w-full
              object-contain
            "
          />
        </div>


        <div
          className="
            min-w-0
            overflow-hidden
          "
        >

          <div
            className="
              truncate
              text-base
              font-bold
              leading-tight
              text-white
            "
          >
            Growth Finance
          </div>


          <div
            className="
              text-[10px]
              font-bold
              uppercase
              tracking-widest
              text-[#4ade80]
            "
          >
            Staff Portal
          </div>

        </div>

      </div>


      {/* ================================================== */}
      {/* ORGANIZATION */}
      {/* ================================================== */}

      {org && (

        <div
          className="
            mx-3
            mt-3
            rounded-lg
            bg-white/5
            px-3
            py-2.5
          "
        >

          <div
            className="
              truncate
              text-xs
              font-semibold
              text-white
            "
          >
            {org.name}
          </div>


          <div
            className="
              mt-0.5
              text-[10px]
              text-gray-400
            "
          >
            {org.currency}
            {' · '}
            {user?.role}
          </div>

        </div>

      )}


      {/* ================================================== */}
      {/* NAVIGATION */}
      {/* ================================================== */}

      <nav
        className="
          mt-1
          flex-1
          space-y-4
          overflow-y-auto
          px-3
          py-3
        "
      >

        {NAV_STAFF.map(
          section => (

            <div
              key={section.section}
            >

              {/* SECTION TITLE */}

              <div
                className="
                  mb-1
                  px-2
                  text-[10px]
                  font-bold
                  uppercase
                  tracking-widest
                  text-gray-500
                "
              >
                {section.section}
              </div>


              {/* SECTION ITEMS */}

              {section.items
                .filter(
                  item =>
                    (
                      !item.adminOnly ||
                      isAdmin
                    ) &&
                    (
                      !item.accountingOnly ||
                      canSeeAccounting
                    )
                )
                .map(
                  item => {

                    const active =
                      isActive(
                        item.href
                      );


                    return (

                      <Link
                        key={
                          item.href
                        }
                        href={
                          item.href
                        }
                        className={`
                          mb-0.5
                          flex
                          items-center
                          gap-2.5
                          rounded-lg
                          px-3
                          py-2
                          text-sm
                          font-medium
                          transition-all

                          ${
                            active
                              ? `
                                bg-teal-500/15
                                text-teal-400
                              `
                              : `
                                text-gray-400
                                hover:bg-white/8
                                hover:text-white
                              `
                          }
                        `}
                      >

                        {/* ICON */}

                        <span
                          className="
                            w-5
                            text-center
                            text-base
                          "
                        >
                          {item.icon}
                        </span>


                        {/* LABEL */}

                        <span
                          className="
                            flex-1
                          "
                        >
                          {item.label}
                        </span>


                        {/* NOTIFICATION BADGE */}

                        {item.href ===
                          '/dashboard/notifications' &&
                          unread > 0 && (

                            <span
                              className="
                                flex
                                h-[18px]
                                min-w-[18px]
                                items-center
                                justify-center
                                rounded-full
                                bg-red-500
                                px-1
                                text-[10px]
                                font-bold
                                text-white
                              "
                            >
                              {
                                unread > 9
                                  ? '9+'
                                  : unread
                              }
                            </span>

                          )}


                        {/* MESSAGE BADGE */}

                        {item.href ===
                          '/dashboard/messages' &&
                          unreadMessages > 0 && (

                            <span
                              className="
                                flex
                                h-[18px]
                                min-w-[18px]
                                items-center
                                justify-center
                                rounded-full
                                bg-teal-500
                                px-1
                                text-[10px]
                                font-bold
                                text-white
                              "
                            >
                              {
                                unreadMessages > 9
                                  ? '9+'
                                  : unreadMessages
                              }
                            </span>

                          )}

                      </Link>

                    );

                  }
                )}

            </div>

          )
        )}


        {/* ================================================== */}
        {/* REGULATORY REPORTS */}
        {/* ================================================== */}

        <div>

          {/* ================================================== */}
          {/* REGULATORY PARENT */}
          {/* ================================================== */}

          <button
            type="button"
            onClick={() =>
              setRegulatoryOpen(
                previous =>
                  !previous
              )
            }
            className={`
              mb-0.5
              flex
              w-full
              items-center
              gap-2.5
              rounded-lg
              px-3
              py-2
              text-sm
              font-medium
              transition-all

              ${
                isRegulatoryRoute
                  ? `
                    bg-teal-500/15
                    text-teal-400
                  `
                  : `
                    text-gray-400
                    hover:bg-white/8
                    hover:text-white
                  `
              }
            `}
          >

            {/* ICON */}

            <span
              className="
                w-5
                text-center
                text-base
              "
            >
              📊
            </span>


            {/* LABEL */}

            <span
              className="
                flex-1
                text-left
              "
            >
              Regulatory Reports
            </span>


            {/* ARROW */}

            <span
              className={`
                text-[10px]
                transition-transform
                duration-200
                ${
                  regulatoryOpen
                    ? 'rotate-180'
                    : ''
                }
              `}
            >
              ▼
            </span>

          </button>


          {/* ================================================== */}
          {/* REGULATORY CHILDREN */}
          {/* ================================================== */}

          {regulatoryOpen && (

            <div
              className="
                ml-4
                space-y-0.5
                border-l
                border-white/10
                pl-2
              "
            >

              {REGULATORY_ITEMS.map(
                item => {

                  const active =
                    isActive(
                      item.href
                    );


                  return (

                    <Link
                      key={
                        item.href
                      }
                      href={
                        item.href
                      }
                      className={`
                        group
                        flex
                        items-center
                        gap-2.5
                        rounded-lg
                        px-3
                        py-2
                        text-sm
                        transition-all

                        ${
                          active
                            ? `
                              bg-teal-500/15
                              font-semibold
                              text-teal-400
                            `
                            : `
                              text-gray-500
                              hover:bg-white/8
                              hover:text-gray-200
                            `
                        }
                      `}
                    >

                      {/* CHILD ICON */}

                      <span
                        className="
                          w-5
                          text-center
                          text-sm
                        "
                      >
                        {item.icon}
                      </span>


                      {/* CHILD LABEL */}

                      <span
                        className="
                          flex-1
                        "
                      >
                        {item.label}
                      </span>


                      {/* ACTIVE INDICATOR */}

                      {active && (

                        <span
                          className="
                            h-1.5
                            w-1.5
                            rounded-full
                            bg-teal-400
                          "
                        />

                      )}

                    </Link>

                  );

                }
              )}

            </div>

          )}

        </div>

      </nav>


      {/* ================================================== */}
      {/* USER FOOTER */}
      {/* ================================================== */}

      <div
        className="
          border-t
          border-white/10
          px-3
          py-3
        "
      >

        <div
          className="
            flex
            cursor-pointer
            items-center
            gap-2.5
            rounded-lg
            px-3
            py-2
            transition-colors
            hover:bg-white/5
          "
          onClick={
            handleLogout
          }
        >

          {/* AVATAR */}

          <div
            className="
              flex
              h-8
              w-8
              flex-shrink-0
              items-center
              justify-center
              rounded-full
              bg-teal-500
              text-sm
              font-bold
              text-[#0D1B2A]
            "
          >
            {user?.name?.[0] ?? 'U'}
          </div>


          {/* USER */}

          <div
            className="
              flex-1
              overflow-hidden
            "
          >

            <div
              className="
                truncate
                text-xs
                font-semibold
                text-white
              "
            >
              {user?.name}
            </div>


            <div
              className="
                text-[10px]
                text-gray-500
              "
            >
              Sign out
            </div>

          </div>


          {/* LOGOUT ICON */}

          <span
            className="
              text-xs
              text-gray-500
            "
          >
            →
          </span>

        </div>

      </div>

    </aside>

  );
}