package bps.budget.customize

import bps.budget.WithIo
import bps.console.menu.Menu
import bps.console.menu.backItem
import bps.console.menu.quitItem
import bps.console.menu.takeAction

val WithIo.customizeMenu: Menu
    get() =
        Menu("Customize!") {
            add(
                takeAction("Create Category Fund") {
                },
            )
            add(
                takeAction("Create Real Fund") {

                },
            )
            add(
                takeAction("Create Draft Fund") {

                },
            )
            add(backItem)
            add(quitItem)
        }
