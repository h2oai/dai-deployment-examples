const toggleOAuth = () => {
    if (Alteryx.Gui.Manager.getDataItem('UseOAuth').getValue()) {
        document.getElementById('usernameBox').style.display = 'none'
        document.getElementById('passwordBox').style.display = 'none'

        document.getElementById('endpointURL').style.display = 'initial'
        document.getElementById('introspectionURL').style.display = 'initial'
        document.getElementById('clientID').style.display = 'initial'
        document.getElementById('refreshToken').style.display = 'initial'
    } else {
        document.getElementById('usernameBox').style.display = 'initial'
        document.getElementById('passwordBox').style.display = 'initial'

        document.getElementById('endpointURL').style.display = 'none'
        document.getElementById('introspectionURL').style.display = 'none'
        document.getElementById('clientID').style.display = 'none'
        document.getElementById('refreshToken').style.display = 'none'
    }
}

Alteryx.Gui.BeforeLoad = function (manager, AlteryxDataItems, json) {
  toggleOAuth()
}

Alteryx.Gui.AfterLoad = function (manager) {
    toggleOAuth()
    Alteryx.Gui.Manager.getDataItem('UseOAuth').registerPropertyListener('value', toggleOAuth)
}
